package io.micrometer.dynatrace;

import static io.micrometer.dynatrace.DynatraceCustomMetric.DynatraceUnit;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * @author Oriol Barcelona
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {

    private final Logger logger = LoggerFactory.getLogger(DynatraceMeterRegistry.class);
    private final DynatraceConfig config;

    /**
     * Metric names for which we have created the custom metric in the API
     */
    private final Set<String> createdCustomMetrics = ConcurrentHashMap.newKeySet();

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        this.config().namingConvention(new DynatraceNamingConvention());

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        final List<DynatraceSerie> series = getMeters().stream()
            .flatMap(meter -> {
                if (meter instanceof Timer) {
                    return createSeries((Timer) meter);
                } else if (meter instanceof FunctionTimer) {
                    return createSeries((FunctionTimer) meter);
                } else if (meter instanceof DistributionSummary) {
                    return createSeries((DistributionSummary) meter);
                } else if (meter instanceof LongTaskTimer) {
                    return createSeries((LongTaskTimer) meter);
                } else {
                    return createSeries(meter);
                }
            })
            .collect(Collectors.toList());

        series.stream()
            .filter(isCustomMetricCreated().negate())
            .map(DynatraceSerie::getMetric)
            .forEach(this::putCustomMetric);

        if (!createdCustomMetrics.isEmpty() && !series.isEmpty()) {
            postCustomMetricValues("{\"series\":[" +
                series.stream()
                    .filter(isCustomMetricCreated())
                    .map(DynatraceSerie::asJson)
                    .collect(joining(",")) +
                "]}");
        }
    }

    private Stream<DynatraceSerie> createSeries(Meter meter) {
        final long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
            .map(ms -> buildSerie(meter.getId(), wallTime, ms.getValue()));
    }

    private Stream<DynatraceSerie> createSeries(LongTaskTimer longTaskTimer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = longTaskTimer.getId();
        return Stream.of(
            buildSerie(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    private Stream<DynatraceSerie> createSeries(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);

        return Stream.of(
            buildSerie(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Stream<DynatraceSerie> createSeries(FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(
            buildSerie(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<DynatraceSerie> createSeries(Timer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);

        return Stream.of(
            buildSerie(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private DynatraceSerie buildSerie(Meter.Id id, long time, Number value) {
        return buildSerie(id, time, value, DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private DynatraceSerie buildSerie(Meter.Id id, long time, Number value, DynatraceUnit unit) {
        return new DynatraceSerie(
            new DynatraceCustomMetric(
                getConventionName(id),
                id.getDescription(),
                unit,
                extractDimensions(getConventionTags(id))),
            extractDimensionValues(getConventionTags(id)), time, value.doubleValue());
    }

    private Set<String> extractDimensions(List<Tag> tags) {
        return tags.stream().map(Tag::getKey).collect(Collectors.toSet());
    }

    private Map<String, String> extractDimensionValues(List<Tag> tags) {
        return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }
    private Predicate<DynatraceSerie> isCustomMetricCreated() {
        return serie -> createdCustomMetrics.contains(serie.getMetric().getMetricId());
    }

    private void putCustomMetric(final DynatraceCustomMetric customMetric) {
        logger.info("[PUT] {}/api/v1/timeseries/{}?api-token={} body: {}",
            config.uri(), customMetric.getMetricId(), config.apiToken(),
            customMetric.asJson());
        createdCustomMetrics.add(customMetric.getMetricId());
    }

    private void postCustomMetricValues(final String body) {
        logger.info("[POST] {}/api/v1/entity/infrastructure/custom/{}?api-token={} body: {}",
            config.uri(), config.deviceId(), config.apiToken(), body);
    }

    private Meter.Id idWithSuffix(final Meter.Id id, final String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
