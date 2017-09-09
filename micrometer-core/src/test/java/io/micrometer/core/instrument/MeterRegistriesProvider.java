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
package io.micrometer.core.instrument;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.time.Duration;
import java.util.stream.Stream;

class MeterRegistriesProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        return Stream.of(
                (Object) new AtlasMeterRegistry(new AtlasConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public String get(String k) {
                        return null;
                    }

                    @Override
                    public Duration step() {
                        return Duration.ofSeconds(1);
                    }
                }, new MockClock()),
                new PrometheusMeterRegistry(k -> null, new CollectorRegistry(true), new MockClock()),
                new SimpleMeterRegistry(new MockClock()),
                new DatadogMeterRegistry(new DatadogConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public String apiKey() {
                        return "DOESNOTMATTER";
                    }

                    @Override
                    public String get(String k) {
                        return null;
                    }

                    @Override
                    public Duration step() {
                        return Duration.ofSeconds(1);
                    }
                }, new MockClock()),
                new GraphiteMeterRegistry(new GraphiteConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public String get(String k) {
                        return null;
                    }
                }, HierarchicalNameMapper.DEFAULT, new MockClock()),
                new InfluxMeterRegistry(new InfluxConfig() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public String get(String k) {
                        return null;
                    }

                    @Override
                    public Duration step() {
                        return Duration.ofSeconds(1);
                    }
                }, new MockClock()),
                new CompositeMeterRegistry(new MockClock()) {{
                    add(new SimpleMeterRegistry(clock));
                }}
        ).map(Arguments::of);
    }
}
