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
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.stats.quantile.GKQuantiles;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        registry.register(new CustomCounter());

        assertThat(registry.getPrometheusRegistry().metricFamilySamples().nextElement().type)
            .describedAs("custom counter with a type of COUNTER")
            .isEqualTo(Collector.Type.COUNTER);
    }

    private class CustomCounter implements Meter {

        @Override
        public String getName() {
            return "custom";
        }

        @Override
        public Iterable<Tag> getTags() {
            return emptyList();
        }

        @Override
        public Type getType() {
            return Type.Counter;
        }

        @Override
        public List<Measurement> measure() {
            return singletonList(new Measurement(getName(), emptyList(), 1));
        }
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
