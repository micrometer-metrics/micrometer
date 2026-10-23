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
package io.micrometer.atlas;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Utils;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MockClock;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.offset;

@ExtendWith(WiremockResolver.class)
class AtlasMeterRegistryTest {

    @Issue("#484")
    @Test
    void publishOneLastTimeOnClose(@WiremockResolver.Wiremock WireMockServer server) {
        AtlasConfig config = new AtlasConfig() {

            @Override
            public @Nullable String get(String k) {
                return null;
            }

            @Override
            public String uri() {
                return server.baseUrl() + "/api/v1/publish";
            }
        };

        server.stubFor(any(anyUrl()));
        new AtlasMeterRegistry(config, Clock.SYSTEM).close();
        server.verify(postRequestedFor(urlEqualTo("/api/v1/publish")));
    }

    @Issue("#2094")
    @Test
    void functionCounter() {
        AtomicLong count = new AtomicLong();

        MockClock clock = new MockClock();
        AtlasMeterRegistry registry = newRegistry(clock);
        try {
            FunctionCounter.builder("test", count, AtomicLong::doubleValue).register(registry);

            Supplier<Double> valueSupplier = () -> pollStatistics(registry, clock, "test").get("count");

            count.addAndGet(60);
            assertThat(valueSupplier.get()).isEqualTo(1.0);

            count.addAndGet(120);
            assertThat(valueSupplier.get()).isEqualTo(2.0);

            count.addAndGet(90);
            assertThat(valueSupplier.get()).isEqualTo(1.5);
        }
        finally {
            registry.close();
        }
    }

    @Test
    void functionTimer() {
        AtomicLong count = new AtomicLong();
        AtomicLong totalTimeMillis = new AtomicLong();

        MockClock clock = new MockClock();
        AtlasMeterRegistry registry = newRegistry(clock);
        try {
            FunctionTimer
                .builder("test", count, AtomicLong::get, c -> totalTimeMillis.doubleValue(), TimeUnit.MILLISECONDS)
                .register(registry);

            count.addAndGet(60);
            totalTimeMillis.addAndGet(120_000);
            assertThat(pollStatistics(registry, clock, "test")).containsOnly(entry("count", 1.0),
                    entry("totalTime", 2.0));

            count.addAndGet(120);
            totalTimeMillis.addAndGet(30_000);
            assertThat(pollStatistics(registry, clock, "test")).containsOnly(entry("count", 2.0),
                    entry("totalTime", 0.5));
        }
        finally {
            registry.close();
        }
    }

    @Test
    void functionCounterWithFractionalValues() {
        AtomicReference<Double> count = new AtomicReference<>(0.0);

        MockClock clock = new MockClock();
        AtlasMeterRegistry registry = newRegistry(clock);
        try {
            FunctionCounter.builder("test", count, AtomicReference::get).register(registry);
            Supplier<Double> valueSupplier = () -> pollStatistics(registry, clock, "test").get("count");

            // Increments of less than 1 would be lost if truncated to a long
            count.set(0.6);
            assertThat(valueSupplier.get()).isCloseTo(0.01, offset(1e-9));

            count.set(1.5);
            assertThat(valueSupplier.get()).isCloseTo(0.015, offset(1e-9));
        }
        finally {
            registry.close();
        }
    }

    @Test
    void counterWithFractionalIncrements() {
        MockClock clock = new MockClock();
        AtlasMeterRegistry registry = newRegistry(clock);
        try {
            Counter counter = registry.counter("test");
            Supplier<Double> valueSupplier = () -> pollStatistics(registry, clock, "test").get("count");

            // Increments of less than 1 would be lost if truncated to a long
            counter.increment(0.5);
            counter.increment(0.5);
            counter.increment(0.5);
            assertThat(valueSupplier.get()).isCloseTo(0.025, offset(1e-9));

            counter.increment(1.5);
            assertThat(valueSupplier.get()).isCloseTo(0.025, offset(1e-9));
        }
        finally {
            registry.close();
        }
    }

    /**
     * Create a registry with a 1 minute step that does not attempt to publish, so it can
     * be closed at the end of the test without any network calls.
     */
    private static AtlasMeterRegistry newRegistry(MockClock clock) {
        return new AtlasMeterRegistry(new AtlasConfig() {

            @Override
            public @Nullable String get(String k) {
                return "atlas.enabled".equals(k) ? "false" : null;
            }

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public Duration lwcStep() {
                return step();
            }
        }, clock);
    }

    /**
     * Polls the function based meters, moves to the next step, and returns the values for
     * the completed step by statistic.
     */
    private static Map<String, Double> pollStatistics(AtlasMeterRegistry registry, MockClock clock, String name) {
        Registry r = registry.getSpectatorRegistry();
        PolledMeter.update(r);
        clock.add(Duration.ofMinutes(1));
        return r.measurements()
            .filter(m -> m.id().name().equals(name))
            .collect(Collectors.toMap(m -> Utils.getTagValue(m.id(), "statistic"), Measurement::value));
    }

}
