package io.micrometer.dynatrace;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        getMeters().stream()
            .filter(customMetricNotYetCreated())
            .map(m -> putCustomMetric(getConventionName(m.getId()), new DynatraceCustomMetric(m.getId())))
            .forEach(createdCustomMetrics::add);

        String body = "{\"series\":[" +
            getMeters().stream().flatMap(this::writeMeter).collect(joining(",")) +
            "]}";
        postCustomMetricValues(config.deviceId(), body);
    }

    private Predicate<Meter> customMetricNotYetCreated() {
        return m -> !createdCustomMetrics.contains(getConventionName(m.getId()));
    }

    private String putCustomMetric(String name, DynatraceCustomMetric customMetric) {
        logger.info("[PUT] {}/api/v1/timeseries/{}?api-token={} body: {}",
            config.uri(), name, config.apiToken(),
            customMetric.editMetadataBody());
        return name;
    }

    private Stream<String> writeMeter(Meter meter) {
        long wallTime = clock.wallTime();
        return StreamSupport.stream(meter.measure().spliterator(), false)
            .map(ms -> writeMeter(meter.getId(), wallTime, ms.getValue()));
    }

    private String writeMeter(Meter.Id id, long wallTime, double value) {
        String s = "{ \"timeSeriesId\":\"" + getConventionName(id) + "\"";

        String dimensions = getConventionTags(id).stream()
            .map(t -> "\"" + t.getKey() + "\":\"" + t.getValue() + "\"")
            .collect(Collectors.joining(","));
        if (dimensions != null && !"".equals(dimensions.trim()))
            s += ",\"dimensions\": {" + dimensions + "}";

        s += ",\"dataPoints\":[[" + wallTime + "," + value + "]]}";
        return s;
    }

    private void postCustomMetricValues(String deviceId, String body) {
        logger.info("[POST] {}/api/v1/entity/infrastructure/custom/{}?api-token={} body: {}",
            config.uri(), deviceId, config.apiToken(), body);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
