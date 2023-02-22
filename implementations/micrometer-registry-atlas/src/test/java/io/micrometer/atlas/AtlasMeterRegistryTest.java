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
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(WiremockResolver.class)
class AtlasMeterRegistryTest {

    @Issue("#484")
    @Test
    void publishOneLastTimeOnClose(@WiremockResolver.Wiremock WireMockServer server) {
        AtlasConfig config = new AtlasConfig() {
            @Nullable
            @Override
            public String get(String k) {
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
        AtlasMeterRegistry registry = new AtlasMeterRegistry(new AtlasConfig() {
            @Nullable
            @Override
            public String get(String k) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofMinutes(1);
            }

            @Override
            public Duration lwcStep() {
                return step();
            }
        }, clock);
        FunctionCounter.builder("test", count, AtomicLong::doubleValue).register(registry);

        Supplier<Double> valueSupplier = () -> {
            AtlasRegistry r = (AtlasRegistry) registry.getSpectatorRegistry();
            PolledMeter.update(r);
            clock.add(Duration.ofMinutes(1));
            return r.measurements()
                .filter(m -> m.id().name().equals("test"))
                .findFirst()
                .map(Measurement::value)
                .orElse(Double.NaN);
        };

        count.addAndGet(60);
        assertThat(valueSupplier.get()).isEqualTo(1.0);

        count.addAndGet(120);
        assertThat(valueSupplier.get()).isEqualTo(2.0);

        count.addAndGet(90);
        assertThat(valueSupplier.get()).isEqualTo(1.5);
    }

}
