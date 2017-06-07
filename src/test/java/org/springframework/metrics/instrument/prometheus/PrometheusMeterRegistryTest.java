/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.prometheus;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.metrics.Issue;
import org.springframework.metrics.export.prometheus.PrometheusFunctions;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.Meters;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.stats.quantile.GKQuantiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * @author Jon Schneider
 */
class PrometheusMeterRegistryTest {
    private PrometheusMeterRegistry registry;
    private CollectorRegistry prometheusRegistry;

    @BeforeEach
    void before() {
        prometheusRegistry = new CollectorRegistry();
        registry = new PrometheusMeterRegistry(prometheusRegistry);
    }

    @DisplayName("quantiles are given as a separate sample with a key of 'quantile'")
    @Test
    void quantiles() {
        registry.timerBuilder("timer")
                .quantiles(GKQuantiles.quantiles(0.5).create())
                .create();

        registry.summaryBuilder("ds")
                .quantiles(GKQuantiles.quantiles(0.5).create())
                .create();

        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("timer", "quantile"));
        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("ds", "quantile"));
    }

    @DisplayName("custom distribution summaries respect varying tags")
    @Issue("#27")
    @Test
    void customSummaries() {
        Arrays.asList("v1", "v2").forEach(v -> {
            registry.summary("s", "k", v).record(1.0);
            assertThat(registry.getPrometheusRegistry().getSampleValue("s_count", new String[]{"k"}, new String[]{v}))
                    .describedAs("distribution summary s with a tag value of %s", v)
                    .isEqualTo(1.0, offset(1e-12));
        });
    }

    @DisplayName("custom meters can be typed")
    @Test
    void typedCustomMeters() {
        AtomicLong n = new AtomicLong();
        registry.register(Meters.build("counter")
                .type(Meter.Type.Counter)
                .create(n, (name, counter) -> singletonList(new Measurement(name, emptyList(), (double) n.incrementAndGet()))));

        assertThat(registry.getPrometheusRegistry().metricFamilySamples().nextElement().type)
                .describedAs("custom counter with a type of COUNTER")
                .isEqualTo(Collector.Type.COUNTER);
    }

    @DisplayName("composite counters")
    @Test
    void compositeCounters() throws InterruptedException {
        Set<Integer> ns = new ConcurrentSkipListSet<>();
        Random r = new Random();

        registry.register(Meters.build("integers")
                .type(Meter.Type.Counter)
                .create(ns, (name, nsRef) -> Arrays.asList(
                        new Measurement(name, singletonList(Tag.of("parity", "even")), ns.stream().filter(n -> n % 2 == 0).count()),
                        new Measurement(name, singletonList(Tag.of("parity", "odd")), ns.stream().filter(n -> n % 2 != 0).count())
                )));

        RouterFunction func = route(GET("/prometheus"), PrometheusFunctions.scrape(registry));
        WebTestClient client = WebTestClient.bindToRouterFunction(func).build();

        client.get()
                .uri("/prometheus")
                .exchange()
                .expectBody()
                .consumeWith(res ->
                        assertThat(new String(res.getResponseBody()))
                                .contains("# TYPE integers counter")
                                .contains("integers{parity=\"even\",}")
                                .contains("integers{parity=\"odd\",}")
                );
    }

    @DisplayName("attempts to register different meter types with the same name fail somewhat gracefully")
    @Test
    void differentMeterTypesWithSameName() {
        registry.timer("m");
        assertThrows(IllegalArgumentException.class, () -> registry.counter("m"));
    }

    private Condition<Enumeration<Collector.MetricFamilySamples>> withNameAndTagKey(String name, String tagKey) {
        return new Condition<>(m -> {
            while (m.hasMoreElements()) {
                Collector.MetricFamilySamples samples = m.nextElement();
                if (samples.samples.stream().anyMatch(s -> s.name.equals(name) && s.labelNames.contains(tagKey))) {
                    return true;
                }
            }
            return false;
        }, "a meter with name `%s` and tag `%s`", name, tagKey);
    }
}
