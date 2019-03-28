package io.micrometer.core.instrument.binder.couchbase;

import com.couchbase.client.core.event.metrics.LatencyMetric;
import com.couchbase.client.core.event.metrics.NetworkLatencyMetricsEvent;
import com.couchbase.client.core.metrics.NetworkLatencyMetricsIdentifier;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

public class CouchbaseMetrics implements MeterBinder {

    private static final TimeUnit TIME_GAUGE_UNIT = TimeUnit.MICROSECONDS;
    private static final String METRIC_PREFIX = "couchbase.network.";
    private final CouchbaseEnvironment env;
    private Map<NetworkLatencyMetricsIdentifier, LatencyMetric> latencies = new ConcurrentHashMap<>();
    private Map<String, Counter> counts = new ConcurrentHashMap<>();

    public CouchbaseMetrics(CouchbaseEnvironment env) {
        this.env = env;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        env.eventBus().get()
                .filter(event -> (event instanceof NetworkLatencyMetricsEvent))
                .flatMapIterable(event -> ((NetworkLatencyMetricsEvent) event).latencies().entrySet())
                .forEach(metricEntry ->  handleMetric(registry, metricEntry.getKey(), metricEntry.getValue()));
    }

    private void handleMetric(MeterRegistry registry,
                              NetworkLatencyMetricsIdentifier id,
                              LatencyMetric metric) {
        LatencyMetric previous = latencies.put(id, metric);
        if (previous == null) {
            registerTimeGauge("max_latency", registry, id,
                    l -> TIME_GAUGE_UNIT.convert(l.get(id).max(), l.get(id).timeUnit()));
            registerTimeGauge("min_latency", registry, id,
                    l -> TIME_GAUGE_UNIT.convert(l.get(id).min(), l.get(id).timeUnit()));
            Counter counter = Counter.builder(METRIC_PREFIX + "count")
                    .tag("host", id.host())
                    .tag("service", id.service())
                    .tag("status", id.status())
                    .tag("request", id.request())
                    .baseUnit("requests")
                    .register(registry);
            counts.put(id.toString(), counter);
        }
        counts.get(id.toString()).increment(metric.count());
    }

    private void registerTimeGauge(String metricName,
                                   MeterRegistry registry,
                                   NetworkLatencyMetricsIdentifier id,
                                   ToDoubleFunction<Map<NetworkLatencyMetricsIdentifier, LatencyMetric>> f) {
        TimeGauge.builder(METRIC_PREFIX + metricName, latencies, TIME_GAUGE_UNIT, f)
                .tag("host", id.host())
                .tag("service", id.service())
                .tag("status", id.status())
                .tag("request", id.request())
                .register(registry);
    }
}
