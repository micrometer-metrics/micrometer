/*
 * Copyright 2024 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.registry.otlp;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.metrics.v1.Metric.DataCase;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.stream.Collectors;

/**
 * A bridge for converting Micrometer meters to OTLP metrics.
 */
class OtlpMetricConverter {

    private final Clock clock;

    private final Duration step;

    private final AggregationTemporality aggregationTemporality;

    private final io.opentelemetry.proto.metrics.v1.AggregationTemporality otlpAggregationTemporality;

    private final TimeUnit baseTimeUnit;

    private final NamingConvention namingConvention;

    private final Map<MetricMetaData, Metric.Builder> metricTypeBuilderMap = new HashMap<>();

    private final long deltaTimeUnixNano;

    @SuppressWarnings("deprecation")
    OtlpMetricConverter(Clock clock, Duration step, TimeUnit baseTimeUnit,
            AggregationTemporality aggregationTemporality, NamingConvention namingConvention) {
        this.clock = clock;
        this.step = step;
        this.aggregationTemporality = aggregationTemporality;
        this.otlpAggregationTemporality = AggregationTemporality.toOtlpAggregationTemporality(aggregationTemporality);
        this.baseTimeUnit = baseTimeUnit;
        this.namingConvention = namingConvention;
        this.deltaTimeUnixNano = (clock.wallTime() / step.toMillis()) * step.toNanos();
    }

    void addMeters(List<Meter> meters) {
        meters.forEach(this::addMeter);
    }

    void addMeter(Meter meter) {
        meter.use(this::writeGauge, this::writeCounter, this::writeHistogramSupport, this::writeHistogramSupport,
                this::writeHistogramSupport, this::writeGauge, this::writeFunctionCounter, this::writeFunctionTimer,
                this::writeMeter);
    }

    List<Metric> getAllMetrics() {
        List<Metric> metrics = new ArrayList<>();
        for (Metric.Builder metricSet : metricTypeBuilderMap.values()) {
            metrics.add(metricSet.build());
        }
        return metrics;
    }

    private void writeMeter(Meter meter) {
        // TODO support writing custom meters
        // one gauge per measurement
        getOrCreateMetricBuilder(meter.getId(), DataCase.GAUGE);
    }

    private void writeGauge(Gauge gauge) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(gauge.getId(), DataCase.GAUGE);
        if (!metricBuilder.hasGauge()) {
            metricBuilder.setGauge(io.opentelemetry.proto.metrics.v1.Gauge.newBuilder());
        }
        metricBuilder.getGaugeBuilder()
            .addDataPoints(NumberDataPoint.newBuilder()
                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(clock.wallTime()))
                .setAsDouble(gauge.value())
                .addAllAttributes(getKeyValuesForId(gauge.getId()))
                .build());
    }

    private void writeCounter(Counter counter) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(counter.getId(), DataCase.SUM);
        setSumDataPoint(metricBuilder, counter, counter::count);
    }

    private void writeFunctionCounter(FunctionCounter functionCounter) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(functionCounter.getId(), DataCase.SUM);
        setSumDataPoint(metricBuilder, functionCounter, functionCounter::count);
    }

    private void writeHistogramSupport(HistogramSupport histogramSupport) {
        Meter.Id id = histogramSupport.getId();
        boolean isTimeBased = isTimeBasedMeter(id);
        HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();

        Iterable<KeyValue> tags = getKeyValuesForId(id);
        long startTimeNanos = getStartTimeNanos(histogramSupport);
        double total = isTimeBased ? histogramSnapshot.total(baseTimeUnit) : histogramSnapshot.total();
        double max = isTimeBased ? histogramSnapshot.max(baseTimeUnit) : histogramSnapshot.max();
        long count = histogramSnapshot.count();

        // if percentiles configured, use summary
        if (histogramSnapshot.percentileValues().length != 0) {
            buildSummaryDataPoint(histogramSupport, tags, startTimeNanos, total, count, isTimeBased, histogramSnapshot);
            return;
        }

        Optional<ExponentialHistogramSnapShot> exponentialHistogramSnapShot = getExponentialHistogramSnapShot(
                histogramSupport);
        if (exponentialHistogramSnapShot.isPresent()) {
            buildExponentialHistogramDataPoint(histogramSupport, tags, startTimeNanos, total, max, count,
                    exponentialHistogramSnapShot.get());
        }
        else {
            buildHistogramDataPoint(histogramSupport, tags, startTimeNanos, total, max, count, isTimeBased,
                    histogramSnapshot);
        }

    }

    private static Optional<ExponentialHistogramSnapShot> getExponentialHistogramSnapShot(
            final HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpHistogramSupport) {
            return Optional.ofNullable(((OtlpHistogramSupport) histogramSupport).getExponentialHistogramSnapShot());
        }

        return Optional.empty();
    }

    private void writeFunctionTimer(FunctionTimer functionTimer) {
        Metric.Builder builder = getOrCreateMetricBuilder(functionTimer.getId(), DataCase.HISTOGRAM);
        HistogramDataPoint.Builder histogramDataPoint = HistogramDataPoint.newBuilder()
            .addAllAttributes(getKeyValuesForId(functionTimer.getId()))
            .setStartTimeUnixNano(getStartTimeNanos((functionTimer)))
            .setTimeUnixNano(getTimeUnixNano())
            .setSum(functionTimer.totalTime(baseTimeUnit))
            .setCount((long) functionTimer.count());

        setHistogramDataPoint(builder, histogramDataPoint.build());
    }

    private boolean isTimeBasedMeter(Meter.Id id) {
        return id.getType() == Meter.Type.TIMER || id.getType() == Meter.Type.LONG_TASK_TIMER;
    }

    private void buildHistogramDataPoint(HistogramSupport histogramSupport, Iterable<KeyValue> tags,
            long startTimeNanos, double total, double max, long count, boolean isTimeBased,
            HistogramSnapshot histogramSnapshot) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(histogramSupport.getId(), DataCase.HISTOGRAM);
        HistogramDataPoint.Builder histogramDataPoint = HistogramDataPoint.newBuilder()
            .addAllAttributes(tags)
            .setStartTimeUnixNano(startTimeNanos)
            .setTimeUnixNano(getTimeUnixNano())
            .setSum(total)
            .setCount(count);

        if (isDelta()) {
            histogramDataPoint.setMax(max);
        }

        // if histogram enabled, add histogram buckets
        for (CountAtBucket countAtBucket : histogramSnapshot.histogramCounts()) {
            if (countAtBucket.bucket() != Double.POSITIVE_INFINITY) {
                // OTLP expects explicit bounds to not contain POSITIVE_INFINITY but
                // there should be a
                // bucket count representing values between last bucket and
                // POSITIVE_INFINITY.
                histogramDataPoint
                    .addExplicitBounds(isTimeBased ? countAtBucket.bucket(baseTimeUnit) : countAtBucket.bucket());
            }
            histogramDataPoint.addBucketCounts((long) countAtBucket.count());
        }

        setHistogramDataPoint(metricBuilder, histogramDataPoint.build());
    }

    private void buildExponentialHistogramDataPoint(HistogramSupport histogramSupport, Iterable<KeyValue> tags,
            long startTimeNanos, double total, double max, long count,
            ExponentialHistogramSnapShot exponentialHistogramSnapShot) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(histogramSupport.getId(),
                DataCase.EXPONENTIAL_HISTOGRAM);
        ExponentialHistogramDataPoint.Builder exponentialDataPoint = ExponentialHistogramDataPoint.newBuilder()
            .addAllAttributes(tags)
            .setStartTimeUnixNano(startTimeNanos)
            .setTimeUnixNano(getTimeUnixNano())
            .setCount(count)
            .setSum(total)
            .setScale(exponentialHistogramSnapShot.scale())
            .setZeroCount(exponentialHistogramSnapShot.zeroCount())
            .setZeroThreshold(exponentialHistogramSnapShot.zeroThreshold());

        // Currently, micrometer doesn't support negative recordings hence we will only
        // add positive buckets.
        if (!exponentialHistogramSnapShot.positive().isEmpty()) {
            exponentialDataPoint.setPositive(ExponentialHistogramDataPoint.Buckets.newBuilder()
                .addAllBucketCounts(exponentialHistogramSnapShot.positive().bucketCounts())
                .setOffset(exponentialHistogramSnapShot.positive().offset())
                .build());
        }

        if (isDelta()) {
            exponentialDataPoint.setMax(max);
        }

        setExponentialHistogramDataPoint(metricBuilder, exponentialDataPoint.build());
    }

    private void buildSummaryDataPoint(HistogramSupport histogramSupport, Iterable<KeyValue> tags, long startTimeNanos,
            double total, long count, boolean isTimeBased, HistogramSnapshot histogramSnapshot) {
        Metric.Builder metricBuilder = getOrCreateMetricBuilder(histogramSupport.getId(), DataCase.SUMMARY);
        SummaryDataPoint.Builder summaryDataPoint = SummaryDataPoint.newBuilder()
            .addAllAttributes(tags)
            .setStartTimeUnixNano(startTimeNanos)
            .setTimeUnixNano(getTimeUnixNano())
            .setSum(total)
            .setCount(count);
        for (ValueAtPercentile percentile : histogramSnapshot.percentileValues()) {
            double value = percentile.value();
            summaryDataPoint.addQuantileValues(SummaryDataPoint.ValueAtQuantile.newBuilder()
                .setQuantile(percentile.percentile())
                .setValue(isTimeBased ? TimeUtils.convert(value, TimeUnit.NANOSECONDS, baseTimeUnit) : value));
        }

        setSummaryDataPoint(metricBuilder, summaryDataPoint);
    }

    private void setSumDataPoint(Metric.Builder builder, Meter meter, DoubleSupplier count) {
        if (!builder.hasSum()) {
            builder.setSum(Sum.newBuilder().setIsMonotonic(true).setAggregationTemporality(otlpAggregationTemporality));
        }

        builder.getSumBuilder()
            .addDataPoints(NumberDataPoint.newBuilder()
                .setStartTimeUnixNano(getStartTimeNanos(meter))
                .setTimeUnixNano(getTimeUnixNano())
                .setAsDouble(count.getAsDouble())
                .addAllAttributes(getKeyValuesForId(meter.getId()))
                .build());
    }

    private void setHistogramDataPoint(Metric.Builder builder, HistogramDataPoint histogramDataPoint) {
        if (!builder.hasHistogram()) {
            builder.setHistogram(Histogram.newBuilder().setAggregationTemporality(otlpAggregationTemporality));
        }
        builder.getHistogramBuilder().addDataPoints(histogramDataPoint);
    }

    private void setExponentialHistogramDataPoint(Metric.Builder builder,
            ExponentialHistogramDataPoint exponentialHistogramDataPoint) {
        if (!builder.hasExponentialHistogram()) {
            builder.setExponentialHistogram(
                    ExponentialHistogram.newBuilder().setAggregationTemporality(otlpAggregationTemporality));
        }
        builder.getExponentialHistogramBuilder().addDataPoints(exponentialHistogramDataPoint);
    }

    private void setSummaryDataPoint(Metric.Builder builder, SummaryDataPoint.Builder summaryDataPoint) {
        if (!builder.hasSummary()) {
            builder.setSummary(Summary.newBuilder());
        }
        builder.getSummaryBuilder().addDataPoints(summaryDataPoint);
    }

    private long getStartTimeNanos(Meter meter) {
        return isDelta() ? deltaTimeUnixNano - step.toNanos() : ((StartTimeAwareMeter) meter).getStartTimeNanos();
    }

    private long getTimeUnixNano() {
        return isDelta() ? deltaTimeUnixNano : TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
    }

    private boolean isDelta() {
        return this.aggregationTemporality == AggregationTemporality.DELTA;
    }

    // VisibleForTesting
    Metric.Builder getOrCreateMetricBuilder(Meter.Id id, DataCase dataCase) {
        String conventionName = id.getConventionName(namingConvention);

        MetricMetaData metricMetaData = new MetricMetaData(dataCase, conventionName, id.getBaseUnit(),
                id.getDescription());
        Metric.Builder builder = metricTypeBuilderMap.get(metricMetaData);
        return builder != null ? builder : createMetricBuilder(metricMetaData);
    }

    private Metric.Builder createMetricBuilder(MetricMetaData metricMetaData) {
        Metric.Builder builder = Metric.newBuilder().setName(metricMetaData.getName());
        if (metricMetaData.getBaseUnit() != null) {
            builder.setUnit(metricMetaData.getBaseUnit());
        }
        if (metricMetaData.getDescription() != null) {
            builder.setDescription(metricMetaData.getDescription());
        }
        metricTypeBuilderMap.put(metricMetaData, builder);
        return builder;
    }

    private Iterable<KeyValue> getKeyValuesForId(Meter.Id id) {
        return id.getConventionTags(namingConvention)
            .stream()
            .map(tag -> createKeyValue(tag.getKey(), tag.getValue()))
            .collect(Collectors.toList());
    }

    private static KeyValue createKeyValue(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    private static class MetricMetaData {

        final DataCase dataCase;

        final String name;

        @Nullable
        final String baseUnit;

        @Nullable
        final String description;

        MetricMetaData(DataCase dataCase, String name, @Nullable String baseUnit, @Nullable String description) {
            this.dataCase = dataCase;
            this.name = name;
            this.baseUnit = baseUnit;
            this.description = description;
        }

        private String getName() {
            return name;
        }

        @Nullable
        private String getBaseUnit() {
            return baseUnit;
        }

        @Nullable
        private String getDescription() {
            return description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MetricMetaData that = (MetricMetaData) o;
            return Objects.equals(dataCase, that.dataCase) && Objects.equals(name, that.name)
                    && Objects.equals(baseUnit, that.baseUnit) && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dataCase, name, baseUnit, description);
        }

    }

}
