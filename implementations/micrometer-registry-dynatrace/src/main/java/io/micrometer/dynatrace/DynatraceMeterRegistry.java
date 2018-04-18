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
import io.micrometer.core.instrument.util.MeterPartition;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private final URL customMetricEndpointTemplate;
    private final URL customDeviceMetricEndpoint;

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);
        this.config = config;

        this.config().namingConvention(new DynatraceNamingConvention());

        try {
            this.customMetricEndpointTemplate = URI.create(config.uri() + "/api/v1/timeseries/").toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        try {
            this.customDeviceMetricEndpoint = URI.create(config.uri() +
                "/api/v1/entity/infrastructure/custom/" + config.deviceId() + "?api-token=" + config.apiToken())
                .toURL();
        } catch (MalformedURLException e) {
            // not possible
            throw new RuntimeException(e);
        }

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            final List<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> series = batch.stream()
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
                .map(Pair::getLeft)
                .filter(this::isCustomMetricNotCreated)
                .forEach(this::putCustomMetric);

            if (!createdCustomMetrics.isEmpty() && !series.isEmpty()) {
                postCustomMetricValues(series.stream().map(Pair::getRight).collect(Collectors.toList()));
            }
        }
    }

    private Stream<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> createSeries(Meter meter) {
        final long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
            .map(ms -> buildSerie(meter.getId(), wallTime, ms.getValue()));
    }

    private Stream<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> createSeries(LongTaskTimer longTaskTimer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = longTaskTimer.getId();
        return Stream.of(
            buildSerie(idWithSuffix(id, "activeTasks"), wallTime, longTaskTimer.activeTasks(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "count"), wallTime, longTaskTimer.duration(getBaseTimeUnit())));
    }

    private Stream<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> createSeries(DistributionSummary summary) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = summary.getId();
        final HistogramSnapshot snapshot = summary.takeSnapshot(false);

        return Stream.of(
            buildSerie(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Stream<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> createSeries(FunctionTimer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();

        return Stream.of(
            buildSerie(idWithSuffix(id, "count"), wallTime, timer.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, timer.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "sum"), wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<Pair<DynatraceCustomMetric,DynatraceTimeSeries>> createSeries(Timer timer) {
        final long wallTime = clock.wallTime();
        final Meter.Id id = timer.getId();
        final HistogramSnapshot snapshot = timer.takeSnapshot(false);

        return Stream.of(
            buildSerie(idWithSuffix(id, "sum"), wallTime, snapshot.total(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "count"), wallTime, snapshot.count(), DynatraceUnit.Count),
            buildSerie(idWithSuffix(id, "avg"), wallTime, snapshot.mean(getBaseTimeUnit())),
            buildSerie(idWithSuffix(id, "max"), wallTime, snapshot.max(getBaseTimeUnit())));
    }

    private Pair<DynatraceCustomMetric,DynatraceTimeSeries> buildSerie(Meter.Id id, long time, Number value) {
        return buildSerie(id, time, value, DynatraceUnit.fromPlural(id.getBaseUnit()));
    }

    private Pair<DynatraceCustomMetric,DynatraceTimeSeries> buildSerie(Meter.Id id, long time, Number value, DynatraceUnit unit) {
        final String metricId = getConventionName(id);
        return Pair.of(
            new DynatraceCustomMetric(metricId, id.getDescription(), unit, extractDimensions(getConventionTags(id))),
            new DynatraceTimeSeries(metricId, time, value.doubleValue(), extractDimensionValues(getConventionTags(id))));
    }

    private Set<String> extractDimensions(List<Tag> tags) {
        return tags.stream().map(Tag::getKey).collect(Collectors.toSet());
    }

    private Map<String, String> extractDimensionValues(List<Tag> tags) {
        return tags.stream().collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    }

    private boolean isCustomMetricNotCreated(final DynatraceCustomMetric metric) {
        return !createdCustomMetrics.contains(metric.getMetricId());
    }

    private boolean isCustomMetricCreated(final DynatraceTimeSeries serie) {
        return createdCustomMetrics.contains(serie.getMetricId());
    }

    private void putCustomMetric(final DynatraceCustomMetric customMetric) {
        try {
            final URL customMetricEndpoint = new URL(customMetricEndpointTemplate +
                customMetric.getMetricId() + "?api-token=" + config.apiToken());

            executeHttpCall(customMetricEndpoint, "PUT", customMetric.asJson(),
                status -> {
                    logger.info("created '{}' as custom metric", customMetric.getMetricId());
                    createdCustomMetrics.add(customMetric.getMetricId());
                },
                (status, errorMsg) -> logger.error("failed to create custom metric '{}', status: {} message: {}",
                    customMetric.getMetricId(), status, errorMsg));
        } catch (final MalformedURLException e) {
            logger.warn("Failed to compose URL for custom metric '{}'", customMetric.getMetricId());
        }
    }

    private void postCustomMetricValues(final List<DynatraceTimeSeries> series) {
        executeHttpCall(customDeviceMetricEndpoint, "POST",
            series.stream()
                .filter(this::isCustomMetricCreated)
                .map(DynatraceTimeSeries::asJson)
                .collect(joining(",")) +
                "]}",
            status -> logger.info("successfully sent {} series to Dynatrace", series.size()),
            (status, errorBody) -> logger.error("failed to send metrics, status: {} body: {}", status, errorBody));
    }

    private void executeHttpCall(final URL url,
                                 final String method,
                                 final String body,
                                 final Consumer<Integer> successHandler,
                                 final BiConsumer<Integer, String> errorHandler) {
        HttpURLConnection con = null;

        try {
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod(method);
            con.setRequestProperty("Content-Type", "application/json");

            con.setDoOutput(true);

            try (final OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            final int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                successHandler.accept(status);
            } else  {
                String errorBody;
                try (InputStream in = con.getErrorStream()) {
                    errorBody = new BufferedReader(new InputStreamReader(in))
                        .lines().collect(joining("\n"));
                }
                errorHandler.accept(status, errorBody);
            }
        } catch (final Throwable e) {
            logger.warn("failed to execute http call to '{}' using method '{}'", url, method, e);
        } finally {
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignore) {
            }
        }
    }

    private Meter.Id idWithSuffix(final Meter.Id id, final String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
