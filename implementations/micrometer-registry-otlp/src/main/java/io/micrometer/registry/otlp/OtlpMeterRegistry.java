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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.*;
import io.opentelemetry.proto.resource.v1.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * Publishes meters in OTLP (OpenTelemetry Protocol) format. HTTP with Protobuf encoding
 * is the only option currently supported.
 *
 * @author Tommy Ludwig
 * @since 1.9.0
 */
public class OtlpMeterRegistry extends PushMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("otlp-metrics-publisher");

    private final InternalLogger logger = InternalLoggerFactory.getInstance(OtlpMeterRegistry.class);

    private final OtlpConfig config;

    private final HttpSender httpSender;

    private final Resource resource;

    public OtlpMeterRegistry() {
        this(OtlpConfig.DEFAULT, Clock.SYSTEM);
    }

    public OtlpMeterRegistry(OtlpConfig config, Clock clock) {
        this(config, clock, new HttpUrlConnectionSender());
    }

    // not public until we decide what we want to expose in public API
    // HttpSender may not be a good idea if we will support a non-HTTP transport
    private OtlpMeterRegistry(OtlpConfig config, Clock clock, HttpSender httpSender) {
        super(config, clock);
        this.config = config;
        this.httpSender = httpSender;
        this.resource = Resource.newBuilder().addAllAttributes(getResourceAttributes()).build();
        config().namingConvention(NamingConvention.dot);
        start(DEFAULT_THREAD_FACTORY);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            List<Metric> metrics = batch.stream()
                .map(meter -> meter.match(this::writeGauge, this::writeCounter, this::writeHistogramSupport,
                        this::writeHistogramSupport, this::writeHistogramSupport, this::writeGauge,
                        this::writeFunctionCounter, this::writeFunctionTimer, this::writeMeter))
                .collect(Collectors.toList());

            try {
                ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
                    .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(this.resource)
                        .addScopeMetrics(ScopeMetrics.newBuilder()
                            // we don't have instrumentation library/version
                            // attached to meters; leave unknown for now
                            // .setScope(InstrumentationScope.newBuilder().setName("").setVersion("").build())
                            .addAllMetrics(metrics)
                            .build())
                        .build())
                    .build();
                this.httpSender.post(this.config.url())
                    .withContent("application/x-protobuf", request.toByteArray())
                    .send();
            }
            catch (Throwable e) {
                logger.warn("Failed to publish metrics to OTLP receiver", e);
            }
        }
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new OtlpCounter(id, this.clock);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        return new OtlpTimer(id, this.clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit());
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new OtlpDistributionSummary(id, this.clock, distributionStatisticConfig, scale, true);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new OtlpFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit,
                getBaseTimeUnit(), this.clock);
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new OtlpFunctionCounter<>(id, obj, countFunction, this.clock);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new OtlpLongTaskTimer(id, this.clock, getBaseTimeUnit(), distributionStatisticConfig);
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

    private Metric writeMeter(Meter meter) {
        // TODO support writing custom meters
        // one gauge per measurement
        return getMetricBuilder(meter.getId()).build();
    }

    // VisibleForTesting
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

    // VisibleForTesting
    Metric writeCounter(Counter counter) {
        return writeSum((StartTimeAwareMeter) counter, counter::count);
    }

    // VisibleForTesting
    Metric writeFunctionCounter(FunctionCounter functionCounter) {
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

    // VisibleForTesting
    Metric writeHistogramSupport(HistogramSupport histogramSupport) {
        Metric.Builder metricBuilder = getMetricBuilder(histogramSupport.getId());
        boolean isTimeBased = histogramSupport instanceof Timer || histogramSupport instanceof LongTaskTimer;
        HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();

        Iterable<? extends KeyValue> tags = getTagsForId(histogramSupport.getId());
        long startTimeNanos = ((StartTimeAwareMeter) histogramSupport).getStartTimeNanos();
        long wallTimeNanos = TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime());
        double total = isTimeBased ? histogramSnapshot.total(getBaseTimeUnit()) : histogramSnapshot.total();
        long count = histogramSnapshot.count();

        // if percentiles configured, use summary
        if (histogramSnapshot.percentileValues().length != 0) {
            SummaryDataPoint.Builder summaryData = SummaryDataPoint.newBuilder()
                .addAllAttributes(tags)
                .setStartTimeUnixNano(startTimeNanos)
                .setTimeUnixNano(wallTimeNanos)
                .setSum(total)
                .setCount(count);
            for (ValueAtPercentile percentile : histogramSnapshot.percentileValues()) {
                summaryData.addQuantileValues(SummaryDataPoint.ValueAtQuantile.newBuilder()
                    .setQuantile(percentile.percentile())
                    .setValue(TimeUtils.convert(percentile.value(), TimeUnit.NANOSECONDS, getBaseTimeUnit())));
            }
            metricBuilder.setSummary(Summary.newBuilder().addDataPoints(summaryData));
            return metricBuilder.build();
        }

        HistogramDataPoint.Builder histogramDataPoint = HistogramDataPoint.newBuilder()
            .addAllAttributes(tags)
            .setStartTimeUnixNano(startTimeNanos)
            .setTimeUnixNano(wallTimeNanos)
            .setSum(total)
            .setCount(count);

        // if histogram enabled, add histogram buckets
        if (histogramSnapshot.histogramCounts().length != 0) {
            for (CountAtBucket countAtBucket : histogramSnapshot.histogramCounts()) {
                histogramDataPoint
                    .addExplicitBounds(isTimeBased ? countAtBucket.bucket(getBaseTimeUnit()) : countAtBucket.bucket());
                histogramDataPoint.addBucketCounts((long) countAtBucket.count());
            }
            metricBuilder.setHistogram(Histogram.newBuilder()
                .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                .addDataPoints(histogramDataPoint));
            return metricBuilder.build();
        }

        return metricBuilder
            .setHistogram(Histogram.newBuilder()
                .setAggregationTemporality(AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE)
                .addDataPoints(histogramDataPoint))
            .build();
    }

    // VisibleForTesting
    Metric writeFunctionTimer(FunctionTimer functionTimer) {
        return getMetricBuilder(functionTimer.getId())
            .setHistogram(Histogram.newBuilder()
                .addDataPoints(HistogramDataPoint.newBuilder()
                    .addAllAttributes(getTagsForId(functionTimer.getId()))
                    .setStartTimeUnixNano(((StartTimeAwareMeter) functionTimer).getStartTimeNanos())
                    .setTimeUnixNano(TimeUnit.MILLISECONDS.toNanos(this.clock.wallTime()))
                    .setSum(functionTimer.totalTime(getBaseTimeUnit()))
                    .setCount((long) functionTimer.count())))
            .build();
    }

    private Metric.Builder getMetricBuilder(Meter.Id id) {
        Metric.Builder builder = Metric.newBuilder().setName(getConventionName(id));
        if (id.getBaseUnit() != null) {
            builder.setUnit(id.getBaseUnit());
        }
        if (id.getDescription() != null) {
            builder.setDescription(id.getDescription());
        }
        return builder;
    }

    private Iterable<? extends KeyValue> getTagsForId(Meter.Id id) {
        return id.getConventionTags(config().namingConvention())
            .stream()
            .map(tag -> createKeyValue(tag.getKey(), tag.getValue()))
            .collect(Collectors.toList());
    }

    // VisibleForTesting
    static KeyValue createKeyValue(String key, String value) {
        return KeyValue.newBuilder().setKey(key).setValue(AnyValue.newBuilder().setStringValue(value)).build();
    }

    // VisibleForTesting
    Iterable<KeyValue> getResourceAttributes() {
        boolean serviceNameProvided = false;
        List<KeyValue> attributes = new ArrayList<>();
        attributes.add(createKeyValue("telemetry.sdk.name", "io.micrometer"));
        attributes.add(createKeyValue("telemetry.sdk.language", "java"));
        String micrometerCoreVersion = MeterRegistry.class.getPackage().getImplementationVersion();
        if (micrometerCoreVersion != null) {
            attributes.add(createKeyValue("telemetry.sdk.version", micrometerCoreVersion));
        }
        for (Map.Entry<String, String> keyValue : this.config.resourceAttributes().entrySet()) {
            if ("service.name".equals(keyValue.getKey())) {
                serviceNameProvided = true;
            }
            attributes.add(createKeyValue(keyValue.getKey(), keyValue.getValue()));
        }
        if (!serviceNameProvided) {
            attributes.add(createKeyValue("service.name", "unknown_service"));
        }
        return attributes;
    }

}
