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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.metrics.Issue;
import org.springframework.metrics.instrument.stats.GKQuantiles;

import java.util.Arrays;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * @author Jon Schneider
 */
class PrometheusMeterRegistryTest {

    @DisplayName("quantiles are given as a separate sample with a key of 'quantile'")
    @Test
    void quantiles() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry();
        CollectorRegistry prometheusRegistry = registry.getPrometheusRegistry();

        registry.timerBuilder("timer")
                .quantiles(GKQuantiles.build().quantile(0.5).create())
                .create();

        registry.distributionSummaryBuilder("ds")
                .quantiles(GKQuantiles.build().quantile(0.5).create())
                .create();

        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("timer", "quantile"));
        assertThat(prometheusRegistry.metricFamilySamples()).has(withNameAndTagKey("ds", "quantile"));
    }

    @DisplayName("custom distribution summaries respect varying tags")
    @Issue("#27")
    @Test
    void customSummaries() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry();

        Arrays.asList("v1", "v2").forEach(v -> {
            registry.distributionSummary("s", "k", v).record(1.0);
            assertThat(registry.getPrometheusRegistry().getSampleValue("s_count", new String[]{"k"}, new String[]{v}))
                    .describedAs("distribution summary s with a tag value of %s", v)
                    .isEqualTo(1.0, offset(1e-12));
        });
    }

    private Condition<Enumeration<Collector.MetricFamilySamples>> withNameAndTagKey(String name, String tagKey) {
        return new Condition<>(m -> {
            while(m.hasMoreElements()) {
                Collector.MetricFamilySamples samples = m.nextElement();
                if(samples.samples.stream().anyMatch(s -> s.name.equals(name) && s.labelNames.contains(tagKey))) {
                    return true;
                }
            }
            return false;
        }, "a meter with name `%s` and tag `%s`", name, tagKey);
    }
}
