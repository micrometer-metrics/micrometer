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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PrometheusRenameFilter}.
 *
 * @author Tommy Ludwig
 */
class PrometheusRenameFilterTest {

    private final PrometheusRenameFilter filter = new PrometheusRenameFilter();

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void doesNotChangeUnrelatedMeter() {
        Meter.Id original = Gauge.builder("system.cpu.count", () -> 1.0).register(registry).getId();
        Meter.Id actual = filter.map(original);
        assertThat(actual).isEqualTo(original);
    }

    @Test
    void doesChangeApplicableMeter() {
        Meter.Id original = Gauge.builder("process.files.open", () -> 1.0).register(registry).getId();
        Meter.Id actual = filter.map(original);
        assertThat(actual).isNotEqualTo(original);
        assertThat(actual.getName()).isEqualTo("process.open.fds");
    }

}
