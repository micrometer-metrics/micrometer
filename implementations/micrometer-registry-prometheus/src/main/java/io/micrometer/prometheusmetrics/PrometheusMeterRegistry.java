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
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.prometheus.metrics.config.PrometheusProperties;
import io.prometheus.metrics.config.PrometheusPropertiesLoader;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

import static java.util.concurrent.TimeUnit.SECONDS;

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
        this.expositionFormats = ExpositionFormats.init(prometheusProperties);
        this.exemplarSamplerFactory = spanContext != null
                ? new DefaultExemplarSamplerFactory(spanContext, prometheusProperties.getExemplarProperties()) : null;

        config().namingConvention(new PrometheusNamingConvention());
        config().onMeterRemoved(this::onMeterRemoved);
    }

    private MeterContext createMeterContext(Meter.Id id) {
        NamingConvention convention = config().namingConvention();
        List<Tag> tags = id.getTags();
        int size = tags.size();
        List<String> tagKeys = new ArrayList<>(size);
        List<String> tagValues = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Tag tag = tags.get(i);
            tagKeys.add(convention.tagKey(tag.getKey()));
            tagValues.add(convention.tagValue(tag.getValue()));
        }
        return new MeterContext(id, tagKeys, tagValues, helpText(id.getDescription()), clock.wallTime());
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
        applyToCollector(id, (collector, context) -> collector.addCounter(context, counter));
        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock,
                distributionStatisticConfig, scale, exemplarSamplerFactory);
        applyToCollector(id, (collector, context) -> collector.addDistributionSummary(context, summary));
        return summary;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        PrometheusTimer timer = new PrometheusTimer(id, clock, distributionStatisticConfig, pauseDetector,
                exemplarSamplerFactory);
        applyToCollector(id, (collector, context) -> collector.addTimer(context, timer));
        return timer;
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj,
            ToDoubleFunction<T> valueFunction) {
        Gauge gauge = new DefaultGauge<>(id, obj, valueFunction);
        applyToCollector(id, (collector, context) -> collector.addGauge(context, gauge));
        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, true);
        applyToCollector(id, (collector, context) -> collector.addLongTaskTimer(context, ltt));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        FunctionTimer ft = new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit());
        applyToCollector(id, (collector, context) -> collector.addFunctionTimer(context, ft));
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        FunctionCounter fc = new CumulativeFunctionCounter<>(id, obj, countFunction);
        applyToCollector(id, (collector, context) -> collector.addFunctionCounter(context, fc));
        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        applyToCollector(id, (collector, context) -> collector.addCustomMeter(context, measurements));
        return new DefaultMeter(id, type, measurements);
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

    private String helpText(@Nullable String description) {
        return prometheusConfig.descriptions() && description != null ? description : " ";
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

    /**
     * Adds a meter to the collector of its Prometheus name, creating and registering the
     * collector if this is the first meter with that name. Everything the collector needs
     * from the naming convention and the registry configuration is resolved once here, so
     * that it does not have to be resolved again on every scrape.
     */
    private void applyToCollector(Meter.Id id, BiConsumer<MicrometerCollector, MeterContext> consumer) {
        collectorMap.compute(getConventionName(id), (name, existingCollector) -> {
            MeterContext context = createMeterContext(id);
            if (existingCollector == null) {
                MicrometerCollector micrometerCollector = new MicrometerCollector(name, id);
                consumer.accept(micrometerCollector, context);
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
                consumer.accept(existingCollector, context);
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
