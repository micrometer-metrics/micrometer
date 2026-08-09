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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.*;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * A bridge for converting Micrometer meters to OTLP metrics.
 */
class OtlpMetricConverter {

    enum MetricType {

        DOUBLE_GAUGE, DOUBLE_SUM, HISTOGRAM, EXPONENTIAL_HISTOGRAM, SUMMARY

    }

    private static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE_INFO = InstrumentationScopeInfo.empty();

    private final Clock clock;

    private final Duration step;

    private final AggregationTemporality aggregationTemporality;

    private final io.opentelemetry.sdk.metrics.data.AggregationTemporality otlpAggregationTemporality;

    private final TimeUnit baseTimeUnit;

    private final NamingConvention namingConvention;

    private final boolean publishMaxGaugeForHistograms;

    private final Resource resource;

    private final Map<MetricMetaData, MetricPointCollector> metricCollectors = new LinkedHashMap<>();

    private final long deltaTimeUnixNano;

    OtlpMetricConverter(Clock clock, Duration step, TimeUnit baseTimeUnit,
            AggregationTemporality aggregationTemporality, NamingConvention namingConvention,
            boolean publishMaxGaugeForHistograms, Resource resource) {
        this.clock = clock;
        this.step = step;
        this.aggregationTemporality = aggregationTemporality;
        this.otlpAggregationTemporality = AggregationTemporality.toOtlpAggregationTemporality(aggregationTemporality);
        this.baseTimeUnit = baseTimeUnit;
        this.namingConvention = namingConvention;
        this.publishMaxGaugeForHistograms = publishMaxGaugeForHistograms;
        this.resource = resource;
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

    List<MetricData> getAllMetrics() {
        List<MetricData> metrics = new ArrayList<>();
        for (Map.Entry<MetricMetaData, MetricPointCollector> entry : metricCollectors.entrySet()) {
            MetricMetaData meta = entry.getKey();
            MetricPointCollector collector = entry.getValue();
            MetricData metricData = collector.toMetricData(resource, INSTRUMENTATION_SCOPE_INFO, meta,
                    otlpAggregationTemporality);
            if (metricData != null) {
                metrics.add(metricData);
            }
        }
        return metrics;
    }

    private void writeMeter(Meter meter) {
        // TODO support writing custom meters
        // one gauge per measurement
        getOrCreateCollector(meter.getId(), MetricType.DOUBLE_GAUGE);
    }

    private void writeGauge(Gauge gauge) {
        DoublePointData point = DoublePointData.create(0, TimeUnit.MILLISECONDS.toNanos(clock.wallTime()),
                getAttributesForId(gauge.getId()), gauge.value(), Collections.emptyList());
        addDoublePointData(gauge.getId(), MetricType.DOUBLE_GAUGE, point);
    }

    private void writeCounter(Counter counter) {
        setSumDataPoint(counter, counter::count, ((OtlpExemplarsSupport) counter)::exemplars);
    }

    private void writeFunctionCounter(FunctionCounter functionCounter) {
        setSumDataPoint(functionCounter, functionCounter::count, Collections::emptyList);
    }

    private void writeHistogramSupport(HistogramSupport histogramSupport) {
        Meter.Id id = histogramSupport.getId();
        boolean isTimeBased = isTimeBasedMeter(id);
        HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();
        List<DoubleExemplarData> exemplars = getExemplars(histogramSupport);

        Attributes attributes = getAttributesForId(id);
        long startTimeNanos = getStartTimeNanos(histogramSupport);
        double total = isTimeBased ? histogramSnapshot.total(baseTimeUnit) : histogramSnapshot.total();
        double max = isTimeBased ? histogramSnapshot.max(baseTimeUnit) : histogramSnapshot.max();
        long count = histogramSnapshot.count();

        if (publishMaxGaugeForHistograms) {
            addMaxGaugeForHistogramSupport(id, attributes, max);
        }

        Optional<ExponentialHistogramSnapShot> exponentialHistogramSnapShot = getExponentialHistogramSnapShot(
                histogramSupport);
        if (histogramSnapshot.percentileValues().length != 0) {
            buildSummaryDataPoint(histogramSupport, attributes, startTimeNanos, total, count, isTimeBased,
                    histogramSnapshot);
        }
        else if (exponentialHistogramSnapShot.isPresent()) {
            buildExponentialHistogramDataPoint(histogramSupport, attributes, startTimeNanos, total, max,
                    exponentialHistogramSnapShot.get(), exemplars);
        }
        else {
            buildHistogramDataPoint(histogramSupport, attributes, startTimeNanos, total, max, isTimeBased,
                    histogramSnapshot, exemplars);
        }

    }

    private static List<DoubleExemplarData> getExemplars(HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpExemplarsSupport) {
            return ((OtlpExemplarsSupport) histogramSupport).exemplars();
        }
        else {
            return Collections.emptyList();
        }
    }

    private static Optional<ExponentialHistogramSnapShot> getExponentialHistogramSnapShot(
            final HistogramSupport histogramSupport) {
        if (histogramSupport instanceof OtlpHistogramSupport) {
            return Optional.ofNullable(((OtlpHistogramSupport) histogramSupport).getExponentialHistogramSnapShot());
        }

        return Optional.empty();
    }

    private void addMaxGaugeForHistogramSupport(Meter.Id id, Attributes attributes, double max) {
        String metricName = id.getName() + ".max";
        Meter.Id maxId = id.withName(metricName);
        DoublePointData point = DoublePointData.create(0, TimeUnit.MILLISECONDS.toNanos(clock.wallTime()), attributes,
                max, Collections.emptyList());
        addDoublePointData(maxId, MetricType.DOUBLE_GAUGE, point);
    }

    private void writeFunctionTimer(FunctionTimer functionTimer) {
        HistogramPointData point = ImmutableHistogramPointData.create(getStartTimeNanos(functionTimer),
                getTimeUnixNano(), getAttributesForId(functionTimer.getId()), functionTimer.totalTime(baseTimeUnit),
                false, 0.0, false, 0.0, Collections.emptyList(),
                Collections.singletonList((long) functionTimer.count()), Collections.emptyList());
        addHistogramPointData(functionTimer.getId(), point);
    }

    private boolean isTimeBasedMeter(Meter.Id id) {
        return id.getType() == Meter.Type.TIMER || id.getType() == Meter.Type.LONG_TASK_TIMER;
    }

    private void buildHistogramDataPoint(HistogramSupport histogramSupport, Attributes attributes, long startTimeNanos,
            double total, double max, boolean isTimeBased, HistogramSnapshot histogramSnapshot,
            List<DoubleExemplarData> exemplars) {
        List<Double> explicitBounds = new ArrayList<>();
        List<Long> bucketCounts = new ArrayList<>();

        if (histogramSnapshot.histogramCounts().length == 0) {
            bucketCounts.add(histogramSnapshot.count());
        }
        else {
            for (CountAtBucket countAtBucket : histogramSnapshot.histogramCounts()) {
                if (countAtBucket.bucket() != Double.POSITIVE_INFINITY) {
                    explicitBounds.add(isTimeBased ? countAtBucket.bucket(baseTimeUnit) : countAtBucket.bucket());
                }
                bucketCounts.add((long) countAtBucket.count());
            }
        }

        HistogramPointData point;
        if (isDelta()) {
            point = ImmutableHistogramPointData.create(startTimeNanos, getTimeUnixNano(), attributes, total, false, 0.0,
                    true, max, explicitBounds, bucketCounts, exemplars);
        }
        else {
            point = ImmutableHistogramPointData.create(startTimeNanos, getTimeUnixNano(), attributes, total, false, 0.0,
                    false, 0.0, explicitBounds, bucketCounts, exemplars);
        }

        addHistogramPointData(histogramSupport.getId(), point);
    }

    private void buildExponentialHistogramDataPoint(HistogramSupport histogramSupport, Attributes attributes,
            long startTimeNanos, double total, double max, ExponentialHistogramSnapShot exponentialHistogramSnapShot,
            List<DoubleExemplarData> exemplars) {
        ExponentialHistogramBuckets positiveBuckets;
        if (!exponentialHistogramSnapShot.positive().isEmpty()) {
            positiveBuckets = ExponentialHistogramBuckets.create(exponentialHistogramSnapShot.scale(),
                    exponentialHistogramSnapShot.positive().offset(),
                    exponentialHistogramSnapShot.positive().bucketCounts());
        }
        else {
            positiveBuckets = ExponentialHistogramBuckets.create(exponentialHistogramSnapShot.scale(), 0,
                    Collections.emptyList());
        }

        ExponentialHistogramBuckets negativeBuckets = ExponentialHistogramBuckets
            .create(exponentialHistogramSnapShot.scale(), 0, Collections.emptyList());

        ExponentialHistogramPointData point;
        if (isDelta()) {
            point = ExponentialHistogramPointData.create(exponentialHistogramSnapShot.scale(), total,
                    exponentialHistogramSnapShot.zeroCount(), false, 0.0, true, max, positiveBuckets, negativeBuckets,
                    startTimeNanos, getTimeUnixNano(), attributes, exemplars);
        }
        else {
            point = ExponentialHistogramPointData.create(exponentialHistogramSnapShot.scale(), total,
                    exponentialHistogramSnapShot.zeroCount(), false, 0.0, false, 0.0, positiveBuckets, negativeBuckets,
                    startTimeNanos, getTimeUnixNano(), attributes, exemplars);
        }

        addExponentialHistogramPointData(histogramSupport.getId(), point);
    }

    private void buildSummaryDataPoint(HistogramSupport histogramSupport, Attributes attributes, long startTimeNanos,
            double total, long count, boolean isTimeBased, HistogramSnapshot histogramSnapshot) {
        List<ValueAtQuantile> valueAtQuantiles = new ArrayList<>();
        for (ValueAtPercentile percentile : histogramSnapshot.percentileValues()) {
            double value = percentile.value(isTimeBased ? baseTimeUnit : TimeUnit.NANOSECONDS);
            valueAtQuantiles.add(ValueAtQuantile.create(percentile.percentile(), value));
        }

        SummaryPointData point = SummaryPointData.create(startTimeNanos, getTimeUnixNano(), attributes, count, total,
                valueAtQuantiles);

        addSummaryPointData(histogramSupport.getId(), point);
    }

    private void setSumDataPoint(Meter meter, DoubleSupplier countSupplier,
            Supplier<List<DoubleExemplarData>> exemplarsSupplier) {
        DoublePointData point = DoublePointData.create(getStartTimeNanos(meter), getTimeUnixNano(),
                getAttributesForId(meter.getId()), countSupplier.getAsDouble(), exemplarsSupplier.get());
        addDoublePointData(meter.getId(), MetricType.DOUBLE_SUM, point);
    }

    private void addDoublePointData(Meter.Id id, MetricType metricType, DoublePointData point) {
        MetricPointCollector collector = getOrCreateCollector(id, metricType);
        collector.doublePoints.add(point);
    }

    private void addHistogramPointData(Meter.Id id, HistogramPointData point) {
        MetricPointCollector collector = getOrCreateCollector(id, MetricType.HISTOGRAM);
        collector.histogramPoints.add(point);
    }

    private void addExponentialHistogramPointData(Meter.Id id, ExponentialHistogramPointData point) {
        MetricPointCollector collector = getOrCreateCollector(id, MetricType.EXPONENTIAL_HISTOGRAM);
        collector.exponentialHistogramPoints.add(point);
    }

    private void addSummaryPointData(Meter.Id id, SummaryPointData point) {
        MetricPointCollector collector = getOrCreateCollector(id, MetricType.SUMMARY);
        collector.summaryPoints.add(point);
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

    private MetricPointCollector getOrCreateCollector(Meter.Id id, MetricType metricType) {
        String conventionName = id.getConventionName(namingConvention);
        MetricMetaData meta = new MetricMetaData(metricType, conventionName, id.getBaseUnit(), id.getDescription());
        return metricCollectors.computeIfAbsent(meta, k -> new MetricPointCollector());
    }

    private Attributes getAttributesForId(Meter.Id id) {
        AttributesBuilder builder = Attributes.builder();
        id.getConventionTags(namingConvention).forEach(tag -> builder.put(tag.getKey(), tag.getValue()));
        return builder.build();
    }

    private static class MetricMetaData {

        final MetricType metricType;

        final String name;

        final @Nullable String baseUnit;

        final @Nullable String description;

        MetricMetaData(MetricType metricType, String name, @Nullable String baseUnit, @Nullable String description) {
            this.metricType = metricType;
            this.name = name;
            this.baseUnit = baseUnit;
            this.description = description;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof MetricMetaData))
                return false;
            MetricMetaData that = (MetricMetaData) o;
            return Objects.equals(metricType, that.metricType) && Objects.equals(name, that.name)
                    && Objects.equals(baseUnit, that.baseUnit) && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(metricType, name, baseUnit, description);
        }

    }

    private static class MetricPointCollector {

        final List<DoublePointData> doublePoints = new ArrayList<>();

        final List<HistogramPointData> histogramPoints = new ArrayList<>();

        final List<ExponentialHistogramPointData> exponentialHistogramPoints = new ArrayList<>();

        final List<SummaryPointData> summaryPoints = new ArrayList<>();

        @Nullable MetricData toMetricData(Resource resource, InstrumentationScopeInfo scope, MetricMetaData meta,
                io.opentelemetry.sdk.metrics.data.AggregationTemporality temporality) {
            String unit = meta.baseUnit != null ? meta.baseUnit : "";
            String description = meta.description != null ? meta.description : "";

            switch (meta.metricType) {
                case DOUBLE_GAUGE:
                    if (doublePoints.isEmpty())
                        return null;
                    return ImmutableMetricData.createDoubleGauge(resource, scope, meta.name, description, unit,
                            GaugeData.createDoubleGaugeData(doublePoints));
                case DOUBLE_SUM:
                    if (doublePoints.isEmpty())
                        return null;
                    return ImmutableMetricData.createDoubleSum(resource, scope, meta.name, description, unit,
                            SumData.createDoubleSumData(true, temporality, doublePoints));
                case HISTOGRAM:
                    if (histogramPoints.isEmpty())
                        return null;
                    return ImmutableMetricData.createDoubleHistogram(resource, scope, meta.name, description, unit,
                            HistogramData.create(temporality, histogramPoints));
                case EXPONENTIAL_HISTOGRAM:
                    if (exponentialHistogramPoints.isEmpty())
                        return null;
                    return ImmutableMetricData.createExponentialHistogram(resource, scope, meta.name, description, unit,
                            ExponentialHistogramData.create(temporality, exponentialHistogramPoints));
                case SUMMARY:
                    if (summaryPoints.isEmpty())
                        return null;
                    return ImmutableMetricData.createDoubleSummary(resource, scope, meta.name, description, unit,
                            SummaryData.create(summaryPoints));
                default:
                    return null;
            }
        }

    }

}
