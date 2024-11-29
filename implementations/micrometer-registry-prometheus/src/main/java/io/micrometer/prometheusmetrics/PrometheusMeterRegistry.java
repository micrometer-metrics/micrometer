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

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.*;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot.GaugeDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.HistogramSnapshot.HistogramDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.InfoSnapshot.InfoDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot;
import io.prometheus.metrics.tracer.common.SpanContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
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

    private final PrometheusConfig prometheusConfig;

    private final PrometheusRegistry registry;

    private final ExpositionFormats expositionFormats;

    private final ConcurrentMap<String, MicrometerCollector> collectorMap = new ConcurrentHashMap<>();

    @Nullable
    private final ExemplarSamplerFactory exemplarSamplerFactory;

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

    private static List<String> tagValues(Meter.Id id) {
        return stream(id.getTagsAsIterable().spliterator(), false).map(Tag::getValue).collect(toList());
    }

    /**
     * @return Content in Prometheus text format for the response body of an endpoint
     * designated for Prometheus to scrape.
     */
    public String scrape() {
        return scrape(Format.TEXT_004.getContentType());
    }

    /**
     * Get the metrics scrape body in a specific content type.
     * @param contentType the scrape Content-Type
     * @return the scrape body
     * @see ExpositionFormats
     */
    public String scrape(String contentType) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            scrape(outputStream, contentType);
            return outputStream.toString();
        }
        catch (IOException e) {
            // This should not happen during writing a ByteArrayOutputStream
            throw new RuntimeException(e);
        }
    }

    /**
     * Scrape to the specified output stream in Prometheus text format.
     * @param outputStream Target that serves the content to be scraped by Prometheus.
     * @throws IOException if writing fails
     */
    public void scrape(OutputStream outputStream) throws IOException {
        scrape(outputStream, Format.TEXT_004.getContentType());
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
            return outputStream.toString();
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
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues,
                    (conventionName,
                            tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                    family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                                    getMetadata(conventionName, id.getDescription()), new CounterDataPointSnapshot(
                                            counter.count(), Labels.of(tagKeys, tagValues), counter.exemplar(), 0))));
        });
        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock,
                distributionStatisticConfig, scale, exemplarSamplerFactory);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> {
                Stream.Builder<MicrometerCollector.Family<?>> families = Stream.builder();

                final ValueAtPercentile[] percentileValues = summary.takeSnapshot().percentileValues();
                final CountAtBucket[] histogramCounts = summary.histogramCounts();
                long count = summary.count();
                double sum = summary.totalAmount();

                if (histogramCounts.length == 0) {
                    Quantiles quantiles = Quantiles.EMPTY;
                    if (percentileValues.length > 0) {
                        List<Quantile> quantileList = new ArrayList<>();
                        for (ValueAtPercentile v : percentileValues) {
                            quantileList.add(new Quantile(v.percentile(), v.value()));
                        }
                        quantiles = Quantiles.of(quantileList);
                    }

                    Exemplars exemplars = summary.exemplars();
                    families.add(new MicrometerCollector.Family<>(conventionName,
                            family -> new SummarySnapshot(family.metadata, family.dataPointSnapshots),
                            getMetadata(conventionName, id.getDescription()), new SummaryDataPointSnapshot(count, sum,
                                    quantiles, Labels.of(tagKeys, tagValues), exemplars, 0)));
                }
                else {
                    List<Double> buckets = new ArrayList<>();
                    List<Number> counts = new ArrayList<>();
                    // TODO: remove this cumulative -> non cumulative conversion
                    // ClassicHistogramBuckets is not cumulative but the
                    // histograms we use are cumulative
                    // so we convert it to non-cumulative just for the
                    // Prometheus client library
                    // can convert it back to cumulative.
                    buckets.add(histogramCounts[0].bucket());
                    counts.add(histogramCounts[0].count());
                    for (int i = 1; i < histogramCounts.length; i++) {
                        CountAtBucket countAtBucket = histogramCounts[i];
                        buckets.add(countAtBucket.bucket());
                        counts.add(countAtBucket.count() - histogramCounts[i - 1].count());
                    }
                    if (Double.isFinite(histogramCounts[histogramCounts.length - 1].bucket())) {
                        // ClassicHistogramBuckets is not cumulative
                        buckets.add(Double.POSITIVE_INFINITY);
                        double infCount = count - histogramCounts[histogramCounts.length - 1].count();
                        counts.add(infCount >= 0 ? infCount : 0);
                    }

                    Exemplars exemplars = summary.exemplars();
                    families.add(new MicrometerCollector.Family<>(conventionName,
                            family -> new io.prometheus.metrics.model.snapshots.HistogramSnapshot(family.metadata,
                                    family.dataPointSnapshots),
                            getMetadata(conventionName, id.getDescription()),
                            new HistogramDataPointSnapshot(ClassicHistogramBuckets.of(buckets, counts), sum,
                                    Labels.of(tagKeys, tagValues), exemplars, 0)));

                    // TODO: Add support back for VictoriaMetrics
                    // Previously we had low-level control so a histogram was just
                    // a bunch of Collector.MetricFamilySamples.Sample
                    // that has an le label for Prometheus and a vmrange label for
                    // Victoria.
                    // That control is gone now, so we don’t have control over the
                    // output and when HistogramDataPointSnapshot is written, the
                    // bucket name is hardcoded to le.
                }

                families.add(new MicrometerCollector.Family<>(conventionName + "_max",
                        family -> new GaugeSnapshot(family.metadata, family.dataPointSnapshots),
                        getMetadata(conventionName + "_max", id.getDescription()),
                        new GaugeDataPointSnapshot(summary.max(), Labels.of(tagKeys, tagValues), null)));

                return families.build();
            });
        });

        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        PrometheusTimer timer = new PrometheusTimer(id, clock, distributionStatisticConfig, pauseDetector,
                exemplarSamplerFactory);
        applyToCollector(id, (collector) -> addDistributionStatisticSamples(id, collector, timer, timer::exemplars,
                tagValues(id), false));
        return timer;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj,
            ToDoubleFunction<T> valueFunction) {
        Gauge gauge = new DefaultGauge<>(id, obj, valueFunction);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            if (id.getName().endsWith(".info")) {
                collector.add(tagValues,
                        (conventionName,
                                tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                        family -> new InfoSnapshot(family.metadata, family.dataPointSnapshots),
                                        getMetadata(conventionName, id.getDescription()),
                                        new InfoDataPointSnapshot(Labels.of(tagKeys, tagValues)))));
            }
            else {
                collector.add(tagValues,
                        (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                family -> new GaugeSnapshot(family.metadata, family.dataPointSnapshots),
                                getMetadata(conventionName, id.getDescription()),
                                new GaugeDataPointSnapshot(gauge.value(), Labels.of(tagKeys, tagValues), null))));
            }
        });
        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, true);
        applyToCollector(id, (collector) -> addDistributionStatisticSamples(id, collector, ltt, () -> Exemplars.EMPTY,
                tagValues(id), true));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit());
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues,
                    (conventionName,
                            tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                    family -> new SummarySnapshot(family.metadata, family.dataPointSnapshots),
                                    getMetadata(conventionName, id.getDescription()),
                                    new SummaryDataPointSnapshot((long) ft.count(), ft.totalTime(getBaseTimeUnit()),
                                            Quantiles.EMPTY, Labels.of(tagKeys, tagValues), null, 0))));
        });
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector
                .add(tagValues,
                        (conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family<>(conventionName,
                                family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                                getMetadata(conventionName, id.getDescription()),
                                new CounterDataPointSnapshot(fc.count(), Labels.of(tagKeys, tagValues), null, 0))));
        });
        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        applyToCollector(id, (collector) -> {
            List<String> tagValues = tagValues(id);
            collector.add(tagValues, (conventionName, tagKeys) -> {
                Stream.Builder<MicrometerCollector.Family<?>> families = Stream.builder();
                List<String> statKeys = new ArrayList<>(tagKeys);
                statKeys.add("statistic");
                for (Measurement measurement : measurements) {
                    List<String> statValues = new ArrayList<>(tagValues);
                    statValues.add(measurement.getStatistic().toString());
                    switch (measurement.getStatistic()) {
                        case TOTAL:
                        case TOTAL_TIME:
                            families.add(customCounterFamily(id, conventionName, "_sum",
                                    Labels.of(statKeys, statValues), measurement.getValue()));
                            break;
                        case COUNT:
                            families.add(customCounterFamily(id, conventionName, "", Labels.of(statKeys, statValues),
                                    measurement.getValue()));
                            break;
                        case MAX:
                            families.add(customGaugeFamily(id, conventionName, "_max", Labels.of(statKeys, statValues),
                                    measurement.getValue()));
                            break;
                        case VALUE:
                        case UNKNOWN:
                            families.add(customGaugeFamily(id, conventionName, "_value",
                                    Labels.of(statKeys, statValues), measurement.getValue()));
                            break;
                        case ACTIVE_TASKS:
                            families.add(customGaugeFamily(id, conventionName, "_active_count",
                                    Labels.of(statKeys, statValues), measurement.getValue()));
                            break;
                        case DURATION:
                            families.add(customGaugeFamily(id, conventionName, "_duration_sum",
                                    Labels.of(statKeys, statValues), measurement.getValue()));
                            break;
                    }
                }
                return families.build();
            });
        });

        return new DefaultMeter(id, type, measurements);
    }

    private MicrometerCollector.Family<CounterDataPointSnapshot> customCounterFamily(Meter.Id id, String conventionName,
            String suffix, Labels labels, double value) {
        return new MicrometerCollector.Family<>(conventionName + suffix,
                family -> new CounterSnapshot(family.metadata, family.dataPointSnapshots),
                getMetadata(conventionName + suffix, id.getDescription()),
                new CounterDataPointSnapshot(value, labels, null, 0));
    }

    private MicrometerCollector.Family<GaugeDataPointSnapshot> customGaugeFamily(Meter.Id id, String conventionName,
            String suffix, Labels labels, double value) {
        return new MicrometerCollector.Family<>(conventionName + suffix,
                family -> new GaugeSnapshot(family.metadata, family.dataPointSnapshots),
                getMetadata(conventionName + suffix, id.getDescription()),
                new GaugeDataPointSnapshot(value, labels, null));
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

    private void addDistributionStatisticSamples(Meter.Id id, MicrometerCollector collector,
            HistogramSupport histogramSupport, Supplier<Exemplars> exemplarsSupplier, List<String> tagValues,
            boolean forLongTaskTimer) {
        collector.add(tagValues, (conventionName, tagKeys) -> {
            Stream.Builder<MicrometerCollector.Family<?>> families = Stream.builder();

            HistogramSnapshot histogramSnapshot = histogramSupport.takeSnapshot();
            ValueAtPercentile[] percentileValues = histogramSnapshot.percentileValues();
            CountAtBucket[] histogramCounts = histogramSnapshot.histogramCounts();
            long count = histogramSnapshot.count();
            double sum = histogramSnapshot.total(getBaseTimeUnit());

            if (histogramCounts.length == 0) {
                Quantiles quantiles = Quantiles.EMPTY;
                if (percentileValues.length > 0) {
                    List<Quantile> quantileList = new ArrayList<>();
                    for (ValueAtPercentile v : percentileValues) {
                        quantileList.add(new Quantile(v.percentile(), v.value(getBaseTimeUnit())));
                    }
                    quantiles = Quantiles.of(quantileList);
                }

                Exemplars exemplars = createExemplarsWithScaledValues(exemplarsSupplier.get());
                families.add(new MicrometerCollector.Family<>(conventionName,
                        family -> new SummarySnapshot(family.metadata, family.dataPointSnapshots),
                        getMetadata(conventionName, id.getDescription()), new SummaryDataPointSnapshot(count, sum,
                                quantiles, Labels.of(tagKeys, tagValues), exemplars, 0)));
            }
            else {
                List<Double> buckets = new ArrayList<>();
                List<Number> counts = new ArrayList<>();
                // TODO: remove this cumulative -> non cumulative conversion
                // ClassicHistogramBuckets is not cumulative but the histograms we
                // use are cumulative
                // so we convert it to non-cumulative just for the Prometheus
                // client library
                // can convert it back to cumulative.
                buckets.add(histogramCounts[0].bucket(getBaseTimeUnit()));
                counts.add(histogramCounts[0].count());
                for (int i = 1; i < histogramCounts.length; i++) {
                    CountAtBucket countAtBucket = histogramCounts[i];
                    buckets.add(countAtBucket.bucket(getBaseTimeUnit()));
                    counts.add(countAtBucket.count() - histogramCounts[i - 1].count());
                }
                if (Double.isFinite(histogramCounts[histogramCounts.length - 1].bucket())) {
                    // ClassicHistogramBuckets is not cumulative
                    buckets.add(Double.POSITIVE_INFINITY);
                    double infCount = count - histogramCounts[histogramCounts.length - 1].count();
                    counts.add(infCount >= 0 ? infCount : 0);
                }

                Exemplars exemplars = createExemplarsWithScaledValues(exemplarsSupplier.get());
                families.add(new MicrometerCollector.Family<>(conventionName,
                        family -> new io.prometheus.metrics.model.snapshots.HistogramSnapshot(forLongTaskTimer,
                                family.metadata, family.dataPointSnapshots),
                        getMetadata(conventionName, id.getDescription()),
                        new HistogramDataPointSnapshot(ClassicHistogramBuckets.of(buckets, counts), sum,
                                Labels.of(tagKeys, tagValues), exemplars, 0)));

                // TODO: Add support back for VictoriaMetrics
                // Previously we had low-level control so a histogram was just
                // a bunch of Collector.MetricFamilySamples.Sample
                // that has an le label for Prometheus and a vmrange label for
                // Victoria.
                // That control is gone now, so we don’t have control over the
                // output and when HistogramDataPointSnapshot is written, the
                // bucket name is hardcoded to le.
            }

            families.add(new MicrometerCollector.Family<>(conventionName + "_max",
                    family -> new GaugeSnapshot(family.metadata, family.dataPointSnapshots),
                    getMetadata(conventionName + "_max", id.getDescription()), new GaugeDataPointSnapshot(
                            histogramSnapshot.max(getBaseTimeUnit()), Labels.of(tagKeys, tagValues), null)));

            return families.build();
        });
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
        MicrometerCollector collector = collectorMap.get(getConventionName(meter.getId()));
        if (collector != null) {
            collector.remove(tagValues(meter.getId()));
            if (collector.isEmpty()) {
                collectorMap.remove(getConventionName(meter.getId()));
                getPrometheusRegistry().unregister(collector);
            }
        }
    }

    private MetricMetadata getMetadata(String name, @Nullable String description) {
        String help = prometheusConfig.descriptions() && description != null ? description : " ";
        // Unit is intentionally not set, see:
        // https://github.com/OpenObservability/OpenMetrics/blob/1386544931307dff279688f332890c31b6c5de36/specification/OpenMetrics.md#unit
        return new MetricMetadata(name, help, null);
    }

    private void applyToCollector(Meter.Id id, Consumer<MicrometerCollector> consumer) {
        collectorMap.compute(getConventionName(id), (name, existingCollector) -> {
            if (existingCollector == null) {
                MicrometerCollector micrometerCollector = new MicrometerCollector(name, id,
                        config().namingConvention());
                consumer.accept(micrometerCollector);
                registry.register(micrometerCollector);
                return micrometerCollector;
            }

            List<String> tagKeys = getConventionTags(id).stream().map(Tag::getKey).collect(toList());
            if (existingCollector.getTagKeys().equals(tagKeys)) {
                consumer.accept(existingCollector);
                return existingCollector;
            }

            meterRegistrationFailed(id,
                    "Prometheus requires that all meters with the same name have the same"
                            + " set of tag keys. There is already an existing meter named '" + id.getName()
                            + "' containing tag keys ["
                            + String.join(", ", collectorMap.get(getConventionName(id)).getTagKeys())
                            + "]. The meter you are attempting to register" + " has keys ["
                            + getConventionTags(id).stream().map(Tag::getKey).collect(joining(", ")) + "].");
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

    private enum Format {

        TEXT_004("text/plain; version=0.0.4; charset=utf-8");

        private final String contentType;

        Format(String contentType) {
            this.contentType = contentType;
        }

        String getContentType() {
            return contentType;
        }

    }

}
