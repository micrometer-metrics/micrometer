/*
 * Copyright 2022 VMware, Inc.
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
import io.micrometer.core.instrument.cumulative.*;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.resource.v1.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Publishes meters in OTLP (OpenTelemetry Protocol) format.
 *
 * @since 1.9.0
 */
public class OtlpMeterRegistry extends PushMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("otlp-metrics-publisher");
    private final OtlpConfig config;
    private final HttpSender httpSender;

    private final Resource resource;

    public OtlpMeterRegistry() {
        this(OtlpConfig.DEFAULT, Clock.SYSTEM);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock) {
        this(config, clock, new HttpUrlConnectionSender());
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock, HttpSender httpSender) {
        super(config, clock);
        this.config = config;
        this.httpSender = httpSender;
        this.resource = Resource.newBuilder()
                .addAllAttributes(getResourceAttributes())
                .build();
        start(DEFAULT_THREAD_FACTORY);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            List<Metric> metrics = batch.stream()
                    .map(meter -> meter.match(
                            this::writeGauge,
                            this::writeCounter,
                            this::writeHistogramSupport,
                            this::writeHistogramSupport,
                            this::writeHistogramSupport,
                            this::writeGauge,
                            this::writeFunctionCounter,
                            this::writeFunctionTimer,
                            this::writeMeter))
                    .collect(Collectors.toList());

            try {
                ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                        .addResourceMetrics(ResourceMetrics.newBuilder()
                                .setResource(this.resource)
                                .addScopeMetrics(ScopeMetrics.newBuilder()
                                        // we don't have instrumentation library/version attached to meters; leave unknown for now
                                        //.setScope(InstrumentationScope.newBuilder().setName("").setVersion("").build())
                                        .addAllMetrics(metrics)
                                        .build())
                                .build())
                        .build();
                this.httpSender.post(this.config.url())
                        .withContent("application/x-protobuf", request.toByteArray())
                        .send();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StartTimeAwareCumulativeCounter(id, this.clock);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new CumulativeTimer(id, this.clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(), true);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new CumulativeDistributionSummary(id, this.clock, distributionStatisticConfig, scale, true);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new StartTimeAwareCumulativeFunctionCounter<>(id, obj, countFunction, this.clock);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new CumulativeHistogramLongTaskTimer(id, this.clock, getBaseTimeUnit(), distributionStatisticConfig);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(this.config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    Metric writeMeter(Meter meter) {
        // TODO
        return getMetricBuilder(meter.getId()).build();
    }

    Metric writeGauge(Gauge gauge) {
        return getMetricBuilder(gauge.getId())
                .setGauge(io.opentelemetry.proto.metrics.v1.Gauge.newBuilder()
                        .addDataPoints(NumberDataPoint.newBuilder()
                                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime()))
                                .setAsDouble(gauge.value())
                                .addAllAttributes(getTagsForId(gauge.getId()))
                                .build()))
                .build();
    }

    private Metric writeCounter(Counter counter) {
        return writeSum((StartTimeAwareMeter) counter, counter::count);
    }

    private Metric writeFunctionCounter(FunctionCounter functionCounter) {
        return writeSum((StartTimeAwareMeter) functionCounter, functionCounter::count);
    }

    private Metric writeSum(StartTimeAwareMeter meter, DoubleSupplier count) {
        return getMetricBuilder(meter.getId())
                .setSum(Sum.newBuilder()
                        .addDataPoints(NumberDataPoint.newBuilder()
                                .setStartTimeUnixNano(meter.getStartTimeNanos())
                                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime()))
                                .setAsDouble(count.getAsDouble())
                                .addAllAttributes(getTagsForId(meter.getId()))
                                .build())
                        .setIsMonotonic(true)
                        .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                        .build())
                .build();
    }

    private Metric writeHistogramSupport(HistogramSupport histogramSupport) {
        Metric.Builder metricBuilder = getMetricBuilder(histogramSupport.getId());
        boolean isTimeBased = histogramSupport instanceof Timer;
        HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();
        long wallTimeNanos = TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime());

        HistogramDataPoint.Builder histogramDataPoint = HistogramDataPoint.newBuilder()
                .addAllAttributes(getTagsForId(histogramSupport.getId()))
                // TODO start time
                .setTimeUnixNano(wallTimeNanos)
                .setSum(isTimeBased ? histogramSnapshot.total(getBaseTimeUnit()) : histogramSnapshot.total())
                .setCount(histogramSnapshot.count());

        // if histogram enabled, add histogram buckets
        if (histogramSnapshot.histogramCounts().length != 0) {
            for (CountAtBucket countAtBucket : histogramSnapshot.histogramCounts()) {
                histogramDataPoint.addExplicitBounds(isTimeBased ? countAtBucket.bucket(getBaseTimeUnit()) : countAtBucket.bucket());
                histogramDataPoint.addBucketCounts((long) countAtBucket.count());
            }
            metricBuilder.setHistogram(Histogram.newBuilder()
                    .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                    .addDataPoints(histogramDataPoint));
            return metricBuilder.build();
        }

        // if percentiles configured, use summary
        if (histogramSnapshot.percentileValues().length != 0) {
            SummaryDataPoint.Builder summaryData = SummaryDataPoint.newBuilder()
                    .addAllAttributes(getTagsForId(histogramSupport.getId()))
                    .setTimeUnixNano(wallTimeNanos)
                    .setSum(histogramSnapshot.total())
                    .setCount(histogramSnapshot.count());
            for (ValueAtPercentile percentile : histogramSnapshot.percentileValues()) {
                summaryData.addQuantileValues(SummaryDataPoint.ValueAtQuantile.newBuilder()
                        .setQuantile(percentile.percentile()).setValue(percentile.value()));
            }
            metricBuilder.setSummary(Summary.newBuilder().addDataPoints(summaryData));
            return metricBuilder.build();
        }

        return metricBuilder.setHistogram(Histogram.newBuilder()
                .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                .addDataPoints(histogramDataPoint)).build();
    }

    private Metric writeFunctionTimer(FunctionTimer functionTimer) {
        return getMetricBuilder(functionTimer.getId())
                .setHistogram(Histogram.newBuilder()
                        .addDataPoints(HistogramDataPoint.newBuilder()
                                .addAllAttributes(getTagsForId(functionTimer.getId()))
                                // TODO start time
                                .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime()))
                                .setSum(functionTimer.totalTime(getBaseTimeUnit()))
                                .setCount((long) functionTimer.count())))
                .build();
    }

    private Metric.Builder getMetricBuilder(Meter.Id id) {
        Metric.Builder builder = Metric.newBuilder()
                .setName(getConventionName(id));
        if (id.getBaseUnit() != null) {
            builder.setUnit(id.getBaseUnit());
        }
        if (id.getDescription() != null) {
            builder.setDescription(id.getDescription());
        }
        return builder;
    }

    private Iterable<? extends KeyValue> getTagsForId(Meter.Id id) {
        return id.getTags().stream()
                .map(tag -> KeyValue.newBuilder()
                        .setKey(tag.getKey())
                        .setValue(AnyValue.newBuilder().setStringValue(tag.getValue()).build())
                        .build())
                .collect(Collectors.toList());
    }

    private Iterable<KeyValue> getResourceAttributes() {
        List<KeyValue> attributes = new ArrayList<>();
        // TODO How to expose configuration of the service.name
        attributes.add(KeyValue.newBuilder().setKey("service.name").setValue(AnyValue.newBuilder().setStringValue("unknown_service")).build());
        attributes.add(KeyValue.newBuilder().setKey("telemetry.sdk.name").setValue(AnyValue.newBuilder().setStringValue("io.micrometer")).build());
        attributes.add(KeyValue.newBuilder().setKey("telemetry.sdk.language").setValue(AnyValue.newBuilder().setStringValue("java")).build());
        String micrometerCoreVersion = MeterRegistry.class.getPackage().getImplementationVersion();
        if (micrometerCoreVersion != null) {
            attributes.add(KeyValue.newBuilder().setKey("telemetry.sdk.version").setValue(AnyValue.newBuilder().setStringValue(MeterRegistry.class.getPackage().getImplementationVersion())).build());
        }
        return attributes;
    }
}
