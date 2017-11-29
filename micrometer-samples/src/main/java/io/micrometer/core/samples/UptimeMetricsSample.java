package io.micrometer.core.samples;

import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.core.samples.utils.SampleRegistries;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.exporter.PushGateway;

import java.io.IOException;

/**
 * Sample to diagnose issue #243
 */
public class UptimeMetricsSample {
    public static void main(String[] args) throws IOException {
        PrometheusMeterRegistry registry = SampleRegistries.prometheusPushgateway();
        registry.config().commonTags("instance", "sample-host");
        new UptimeMetrics().bindTo(registry);
        new PushGateway("localhost:9091").pushAdd(registry.getPrometheusRegistry(), "samples");
    }
}
