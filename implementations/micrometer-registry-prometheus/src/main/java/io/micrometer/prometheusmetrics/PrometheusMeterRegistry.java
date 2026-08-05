/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.prometheusmetrics;

import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.MetricType;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.*;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot.InfoDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link MeterRegistry} for Prometheus.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Jonatan Ivanov
 * @since 1.13.0
 */
public class PrometheusMeterRegistry extends MeterRegistry {

    private static final WarnThenDebugLogger meterRegistrationFailureLogger = new WarnThenDebugLogger(
            PrometheusMeterRegistry.class);

    private static final String TEXT_004_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final PrometheusConfig prometheusConfig;

    private final PrometheusRegistry registry;

    private final ExpositionFormats expositionFormats;

    private final ConcurrentMap<String, MicrometerCollector> collectorMap = new ConcurrentHashMap<>();

    private final @Nullable ExemplarSamplerFactory exemplarSamplerFactory;

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new PrometheusRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, PrometheusRegistry registry, Clock clock) {
        this(config, registry, clock, null);
    }

    /**
     * Create a {@code PrometheusMeterRegistry} instance.
     * @param config configuration
     * @param registry prometheus registry
     * @param clock clock
     * @param spanContext span context that interacts with the used tracing library
     */
    public PrometheusMeterRegistry(PrometheusConfig config, PrometheusRegistry registry, Clock clock,
            @Nullable SpanContext spanContext) {
        super(clock);

        config.requireValid();

        this.prometheusConfig = config;
        this.registry = registry;
        PrometheusProperties prometheusProperties = config.prometheusProperties() != null
                ? PrometheusPropertiesLoader.load(config.prometheusProperties()) : PrometheusPropertiesLoader.load();
        this.expositionFormats = ExpositionFormats.init(prometheusProperties.getExporterProperties());
        this.exemplarSamplerFactory = spanContext != null
                ? new DefaultExemplarSamplerFactory(spanContext, prometheusProperties.getExemplarProperties()) : null;

        config().namingConvention(new PrometheusNamingConvention());
        config().onMeterRemoved(this::onMeterRemoved);
    }

    private List<String> tagKeys(Meter.Id id) {
        return getConventionTags(id).stream().map(Tag::getKey).collect(toList());
    }

    private static List<String> tagValues(Meter.Id id) {
        return stream(id.getTagsAsIterable().spliterator(), false).map(Tag::getValue).collect(toList());
    }

    /**
     * @return Content in Prometheus text format for the response body of an endpoint
     * designated for Prometheus to scrape.
     */
    public String scrape() {
        return scrape(TEXT_004_CONTENT_TYPE);
    }

    /**
     * Get the metrics scrape body in a specific content type.
     * @param contentType the scrape Content-Type
     * @return the scrape body
     * @see ExpositionFormats
     */
    public String scrape(String contentType) {
        return scrape(contentType, null);
    }

    /**
     * Scrape to the specified output stream in Prometheus text format.
     * @param outputStream Target that serves the content to be scraped by Prometheus.
     * @throws IOException if writing fails
     */
    public void scrape(OutputStream outputStream) throws IOException {
        scrape(outputStream, TEXT_004_CONTENT_TYPE);
    }

    /**
     * Write the metrics scrape body in a specific content type to the given output
     * stream.
     * @param outputStream where to write the scrape body
     * @param contentType the Content-Type of the scrape
     * @throws IOException if writing fails
     * @see ExpositionFormats
     */
    public void scrape(OutputStream outputStream, String contentType) throws IOException {
        scrape(outputStream, contentType, registry.scrape());
    }

    private void scrape(OutputStream outputStream, String contentType, MetricSnapshots snapshots) throws IOException {
        expositionFormats.findWriter(contentType).write(outputStream, snapshots);
    }

    /**
     * Return text for scraping.
     * @param contentType the Content-Type of the scrape.
     * @param includedNames Sample names to be included. All samples will be included if
     * {@code null}.
     * @return Content that should be included in the response body for an endpoint
     * designated for Prometheus to scrape from.
     * @see ExpositionFormats
     */
    public String scrape(String contentType, @Nullable Set<String> includedNames) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            scrape(outputStream, contentType, includedNames);
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
        catch (IOException e) {
            // This should not happen during writing a ByteArrayOutputStream
            throw new RuntimeException(e);
        }
    }

    /**
     * Scrape to the specified output stream.
     * @param outputStream Target that serves the content to be scraped by Prometheus.
     * @param contentType the Content-Type of the scrape.
     * @param includedNames Sample names to be included. All samples will be included if
     * {@code null}.
     * @throws IOException if writing fails
     * @see ExpositionFormats
     */
    public void scrape(OutputStream outputStream, String contentType, @Nullable Set<String> includedNames)
            throws IOException {
        MetricSnapshots snapshots = includedNames != null ? registry.scrape(includedNames::contains)
                : registry.scrape();
        scrape(outputStream, contentType, snapshots);
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        PrometheusCounter counter = new PrometheusCounter(id, exemplarSamplerFactory);
        long createdTimestampMillis = clock.wallTime();
        applyToCollector(id, (collector) -> {
            List<String> tagKeys = tagKeys(id);
            Labels labels = Labels.of(tagKeys, tagValues(id));
            String conventionName = collector.conventionName;
            MetricFamilyDescriptor familyDescriptor = familyDescriptor(collector, MetricType.COUNTER, conventionName,
                    tagKeys, id.getDescription());
            collector.add(id, samples -> samples.accept(familyDescriptor,
                    new CounterDataPointSnapshot(counter.count(), labels, counter.exemplar(), createdTimestampMillis)),
                    familyDescriptor);
        });
        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock,
                distributionStatisticConfig, scale, exemplarSamplerFactory);
        applyToCollector(id,
                (collector) -> addDistributionStatisticSamples(id, collector, summary, summary::exemplars, null));
        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        PrometheusTimer timer = new PrometheusTimer(id, clock, distributionStatisticConfig, pauseDetector,
                exemplarSamplerFactory);
        applyToCollector(id, (collector) -> addDistributionStatisticSamples(id, collector, timer,
                () -> createExemplarsWithScaledValues(timer.exemplars()), getBaseTimeUnit()));
        return timer;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj,
            ToDoubleFunction<T> valueFunction) {
        Gauge gauge = new DefaultGauge<>(id, obj, valueFunction);
        applyToCollector(id, (collector) -> {
            List<String> tagKeys = tagKeys(id);
            Labels labels = Labels.of(tagKeys, tagValues(id));
            String conventionName = collector.conventionName;
            MetricFamilyDescriptor familyDescriptor;
            if (id.getName().endsWith(".info")) {
                familyDescriptor = familyDescriptor(collector, MetricType.INFO, conventionName, tagKeys,
                        id.getDescription());
                collector.add(id, samples -> samples.accept(familyDescriptor, new InfoDataPointSnapshot(labels)),
                        familyDescriptor);
            }
            else {
                familyDescriptor = familyDescriptor(collector, MetricType.GAUGE, conventionName, tagKeys,
                        id.getDescription());
                collector.add(id, samples -> samples.accept(familyDescriptor,
                        new GaugeDataPointSnapshot(gauge.value(), labels, null)), familyDescriptor);
            }
        });
        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, true);
        applyToCollector(id, (collector) -> addDistributionStatisticSamples(id, collector, ltt, () -> Exemplars.EMPTY,
                getBaseTimeUnit()));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit());
        long createdTimestampMillis = clock.wallTime();
        applyToCollector(id, (collector) -> {
            List<String> tagKeys = tagKeys(id);
            Labels labels = Labels.of(tagKeys, tagValues(id));
            String conventionName = collector.conventionName;
            MetricFamilyDescriptor familyDescriptor = familyDescriptor(collector, MetricType.SUMMARY, conventionName,
                    tagKeys, id.getDescription());
            collector.add(id,
                    samples -> samples.accept(familyDescriptor, new SummaryDataPointSnapshot((long) ft.count(),
                            ft.totalTime(getBaseTimeUnit()), Quantiles.EMPTY, labels, null, createdTimestampMillis)),
                    familyDescriptor);
        });
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
        long createdTimestampMillis = clock.wallTime();
        applyToCollector(id, (collector) -> {
            List<String> tagKeys = tagKeys(id);
            Labels labels = Labels.of(tagKeys, tagValues(id));
            String conventionName = collector.conventionName;
            MetricFamilyDescriptor familyDescriptor = familyDescriptor(collector, MetricType.COUNTER, conventionName,
                    tagKeys, id.getDescription());
            collector.add(id,
                    samples -> samples.accept(familyDescriptor,
                            new CounterDataPointSnapshot(fc.count(), labels, null, createdTimestampMillis)),
                    familyDescriptor);
        });
        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        long createdTimestampMillis = clock.wallTime();
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            List<String> statKeys = new ArrayList<>(tagKeys(id));
            statKeys.add("statistic");

            // precompute the family descriptor and labels per statistic; measurements
            // with statistics that map to the same Prometheus name (e.g. TOTAL and
            // TOTAL_TIME) share one family descriptor
            Map<String, MetricFamilyDescriptor> descriptorsByName = new LinkedHashMap<>();
            Map<Statistic, MeasurementFamily> familiesByStatistic = new EnumMap<>(Statistic.class);
            for (Measurement measurement : measurements) {
                familiesByStatistic.computeIfAbsent(measurement.getStatistic(), statistic -> {
                    MetricFamilyDescriptor descriptor = customMeterDescriptor(collector, id, collector.conventionName,
                            statistic, statKeys);
                    MetricFamilyDescriptor existing = descriptorsByName.putIfAbsent(descriptor.getPrometheusName(),
                            descriptor);
                    List<String> statValues = new ArrayList<>(tagValues);
                    statValues.add(statistic.toString());
                    return new MeasurementFamily(existing != null ? existing : descriptor,
                            Labels.of(statKeys, statValues));
                });
            }

            collector.add(id, samples -> {
                for (Measurement measurement : measurements) {
                    MeasurementFamily family = requireNonNull(familiesByStatistic.get(measurement.getStatistic()));
                    if (family.descriptor.getType() == MetricType.COUNTER) {
                        samples.accept(family.descriptor, new CounterDataPointSnapshot(measurement.getValue(),
                                family.labels, null, createdTimestampMillis));
                    }
                    else {
                        samples.accept(family.descriptor,
                                new GaugeDataPointSnapshot(measurement.getValue(), family.labels, null));
                    }
                }
            }, descriptorsByName.values().toArray(new MetricFamilyDescriptor[0]));
        });

        return new DefaultMeter(id, type, measurements);
    }

    private MetricFamilyDescriptor customMeterDescriptor(MicrometerCollector collector, Meter.Id id,
            String conventionName, Statistic statistic, Collection<String> labelNames) {
        switch (statistic) {
            case TOTAL:
            case TOTAL_TIME:
                return familyDescriptor(collector, MetricType.COUNTER, conventionName + "_sum", labelNames,
                        id.getDescription());
            case COUNT:
                return familyDescriptor(collector, MetricType.COUNTER, conventionName, labelNames, id.getDescription());
            case MAX:
                return familyDescriptor(collector, MetricType.GAUGE, conventionName + "_max", labelNames,
                        id.getDescription());
            case VALUE:
            case UNKNOWN:
                return familyDescriptor(collector, MetricType.GAUGE, conventionName + "_value", labelNames,
                        id.getDescription());
            case ACTIVE_TASKS:
                return familyDescriptor(collector, MetricType.GAUGE, conventionName + "_active_count", labelNames,
                        id.getDescription());
            case DURATION:
                return familyDescriptor(collector, MetricType.GAUGE, conventionName + "_duration_sum", labelNames,
                        id.getDescription());
            default:
                throw new IllegalArgumentException("Unsupported meter statistic: " + statistic);
        }
    }

    /**
     * The Prometheus metric family and precomputed labels for the measurements of one
     * {@link Statistic} of a custom meter.
     */
    private static final class MeasurementFamily {

        private final MetricFamilyDescriptor descriptor;

        private final Labels labels;

        private MeasurementFamily(MetricFamilyDescriptor descriptor, Labels labels) {
            this.descriptor = descriptor;
            this.labels = labels;
        }

    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return SECONDS;
    }

    /**
     * @return The underlying Prometheus {@link PrometheusRegistry}.
     */
    public PrometheusRegistry getPrometheusRegistry() {
        return registry;
    }

    /**
     * Adds samples for meters with distribution statistics, i.e. timers, long task
     * timers, and distribution summaries.
     * @param timeUnit if {@code null}, snapshot values are used as-is (distribution
     * summaries); otherwise, time-based snapshot values are converted to this unit
     * (timers, long task timers)
     */
    private void addDistributionStatisticSamples(Meter.Id id, MicrometerCollector collector,
            HistogramSupport histogramSupport, Supplier<Exemplars> exemplarsSupplier, @Nullable TimeUnit timeUnit) {
        long createdTimestampMillis = clock.wallTime();
        List<String> tagKeys = tagKeys(id);
        Labels labels = Labels.of(tagKeys, tagValues(id));
        MetricType primaryType = histogramSupport.takeSnapshot().histogramCounts().length == 0 ? MetricType.SUMMARY
                : MetricType.HISTOGRAM;
        String conventionName = collector.conventionName;
        MetricFamilyDescriptor familyDescriptor = familyDescriptor(collector, primaryType, conventionName, tagKeys,
                id.getDescription());
        MetricFamilyDescriptor familyDescriptorMax = familyDescriptor(collector, MetricType.GAUGE, conventionName + "_max",
                tagKeys, id.getDescription());

        collector.add(id, samples -> {
            HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();
            CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
            long count = histogramSnapshot.count();
            double sum = timeUnit == null ? histogramSnapshot.total() : histogramSnapshot.total(timeUnit);

            if (histogramCounts.length == 0) {
                samples.accept(familyDescriptor,
                        new SummaryDataPointSnapshot(count, sum,
                                quantiles(histogramSnapshot.percentileValues(), timeUnit), labels,
                                exemplarsSupplier.get(), createdTimestampMillis));
            }
            else {
                samples.accept(familyDescriptor,
                        new HistogramDataPointSnapshot(nonCumulativeBuckets(histogramCounts, count, timeUnit), sum,
                                labels, exemplarsSupplier.get(), createdTimestampMillis));

                // TODO: Add support back for VictoriaMetrics
                // Previously we had low-level control so a histogram was just
                // a bunch of Collector.MetricFamilySamples.Sample
                // that has an le label for Prometheus and a vmrange label for
                // Victoria.
                // That control is gone now, so we don’t have control over the
                // output and when HistogramDataPointSnapshot is written, the
                // bucket name is hardcoded to le.
            }

            double max = timeUnit == null ? histogramSnapshot.max() : histogramSnapshot.max(timeUnit);
            samples.accept(familyDescriptorMax, new GaugeDataPointSnapshot(max, labels, null));
        }, familyDescriptor, familyDescriptorMax);
    }

    private static Quantiles quantiles(ValueAtPercentile[] percentileValues, @Nullable TimeUnit timeUnit) {
        if (percentileValues.length == 0) {
            return Quantiles.EMPTY;
        }
        List<Quantile> quantiles = new ArrayList<>(percentileValues.length);
        for (ValueAtPercentile percentileValue : percentileValues) {
            quantiles.add(new Quantile(percentileValue.percentile(),
                    timeUnit == null ? percentileValue.value() : percentileValue.value(timeUnit)));
        }
        return Quantiles.of(quantiles);
    }

    // TODO: remove this cumulative -> non cumulative conversion
    // ClassicHistogramBuckets is not cumulative but the histograms we use are cumulative
    // so we convert it to non-cumulative just for the Prometheus client library
    // can convert it back to cumulative.
    private static ClassicHistogramBuckets nonCumulativeBuckets(CountAtBucket[] histogramCounts, long count,
            @Nullable TimeUnit timeUnit) {
        List<Double> buckets = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        buckets.add(bucket(histogramCounts[0], timeUnit));
        counts.add(histogramCounts[0].count());
        for (int i = 1; i < histogramCounts.length; i++) {
            buckets.add(bucket(histogramCounts[i], timeUnit));
            counts.add(histogramCounts[i].count() - histogramCounts[i - 1].count());
        }
        if (Double.isFinite(histogramCounts[histogramCounts.length - 1].bucket())) {
            // ClassicHistogramBuckets is not cumulative
            buckets.add(Double.POSITIVE_INFINITY);
            double infCount = count - histogramCounts[histogramCounts.length - 1].count();
            counts.add(infCount >= 0 ? infCount : 0);
        }
        return ClassicHistogramBuckets.of(buckets, counts);
    }

    private static double bucket(CountAtBucket countAtBucket, @Nullable TimeUnit timeUnit) {
        return timeUnit == null ? countAtBucket.bucket() : countAtBucket.bucket(timeUnit);
    }

    private MetricFamilyDescriptor familyDescriptor(MicrometerCollector collector, MetricType metricType, String name,
            Collection<String> labelNames, @Nullable String description) {
        return collector.getOrCreateDescriptor(metricType, name,
                () -> familyDescriptor(metricType, name, labelNames, description));
    }

    private MetricFamilyDescriptor familyDescriptor(MetricType metricType, String name, Collection<String> labelNames,
            @Nullable String description) {
        // NB: For custom meters that use `getMetadata`, the metadata inside the
        // descriptor should be consistent with the one returned by `getMetadata`.

        // Unit is intentionally not set, see:
        // https://github.com/OpenObservability/OpenMetrics/blob/1386544931307dff279688f332890c31b6c5de36/specification/OpenMetrics.md#unit
        return MetricFamilyDescriptor.of(metricType, name).help(helpText(description)).labelNames(labelNames).build();
    }

    private String helpText(@Nullable String description) {
        return prometheusConfig.descriptions() && description != null ? description : " ";
    }

    private Exemplars createExemplarsWithScaledValues(Exemplars exemplars) {
        return Exemplars.of(StreamSupport.stream(exemplars.spliterator(), false)
            .map(exemplar -> createExemplarWithNewValue(
                    TimeUtils.convert(exemplar.getValue(), NANOSECONDS, getBaseTimeUnit()), exemplar))
            .collect(toList()));
    }

    private Exemplar createExemplarWithNewValue(double newValue, Exemplar exemplar) {
        return Exemplar.builder()
            .value(newValue)
            .labels(exemplar.getLabels())
            .timestampMillis(exemplar.getTimestampMillis())
            .build();
    }

    private void onMeterRemoved(Meter meter) {
        String conventionName = getConventionName(meter.getId());
        MicrometerCollector collector = collectorMap.get(conventionName);
        if (collector != null) {
            collector.remove(meter.getId());
            if (collector.isEmpty()) {
                collectorMap.remove(conventionName);
                getPrometheusRegistry().unregister(collector);
            }
        }
    }

    private void applyToCollector(Meter.Id id, Consumer<MicrometerCollector> consumer) {
        collectorMap.compute(getConventionName(id), (name, existingCollector) -> {
            if (existingCollector == null) {
                MicrometerCollector micrometerCollector = new MicrometerCollector(name, id);
                consumer.accept(micrometerCollector);
                try {
                    registry.register(micrometerCollector);
                    return micrometerCollector;
                }
                catch (IllegalArgumentException e) {
                    meterRegistrationFailed(id, e.getMessage());
                    return null;
                }
            }

            if (!existingCollector.getOriginalId().getName().equals(id.getName())) {
                meterRegistrationFailed(id, "A meter with the same Prometheus name (" + name + ") is already "
                        + "registered. Registering this meter with a different Micrometer name that maps to the same Prometheus name "
                        + "would fail with an exception on scrape.");
                return existingCollector;
            }

            Meter.Type type = existingCollector.getOriginalId().getType();
            if (!type.equals(id.getType())) {
                meterRegistrationFailed(id,
                        "Prometheus requires that all meters with the same name have the same"
                                + " type. There is already an existing meter named '" + name + "' that is a " + type
                                + ". The meter you are attempting to register" + " is a " + id.getType() + ".");
                return existingCollector;
            }

            try {
                consumer.accept(existingCollector);
            }
            catch (IllegalArgumentException e) {
                meterRegistrationFailed(id, e.getMessage());
            }
            return existingCollector;
        });
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(prometheusConfig.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }

    /**
     * For use with
     * {@link io.micrometer.core.instrument.MeterRegistry.Config#onMeterRegistrationFailed(BiConsumer)
     * MeterRegistry.Config#onMeterRegistrationFailed(BiConsumer)} when you want meters
     * with the same name but different tags to cause an unchecked exception.
     * @return This registry
     */
    public PrometheusMeterRegistry throwExceptionOnRegistrationFailure() {
        config().onMeterRegistrationFailed((id, reason) -> {
            throw new IllegalArgumentException(reason);
        });

        return this;
    }

    @Override
    protected void meterRegistrationFailed(Meter.Id id, @Nullable String reason) {
        meterRegistrationFailureLogger.log(() -> createMeterRegistrationFailureMessage(id, reason));

        super.meterRegistrationFailed(id, reason);
    }

    private static String createMeterRegistrationFailureMessage(Meter.Id id, @Nullable String reason) {
        String message = String.format("The meter (%s) registration has failed", id);
        if (reason != null) {
            message += ": " + reason;
        }
        else {
            message += ".";
        }
        return message;
    }

}
