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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MockClock;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusMeterRegistryCustomizeTest {

    private final CollectorRegistry prometheusRegistry = new CollectorRegistry(true);

    private final MockClock clock = new MockClock();

    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT,
            prometheusRegistry, clock) {

        @Override
        protected String getConventionName(Meter.Id id) {
            return "custom_prefix_" + super.getConventionName(id);
        }
    };

    @DisplayName("registered counter collector name is the same that calculated by PrometheusMeterRegistry")
    @Test
    void customNamedCollectorName() {
        Counter.builder("counter").description("my counter").register(registry);
        assertThat(this.registry.getPrometheusRegistry().metricFamilySamples().nextElement().name)
            .isEqualTo("custom_prefix_counter");
    }

}
