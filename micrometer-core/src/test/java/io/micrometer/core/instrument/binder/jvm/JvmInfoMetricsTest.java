/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JvmInfoMetrics}.
 *
 * @author Erin Schnabel
 */
class JvmInfoMetricsTest {

    @Test
    void assertJvmInfo() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new JvmInfoMetrics().bindTo(registry);

        Collection<Gauge> gauges = Search.in(registry).name("jvm.info").gauges();
        assertThat(gauges.size()).isEqualTo(1);

        Gauge jvmInfo = gauges.iterator().next();
        assertThat(jvmInfo.value()).isEqualTo(1L);

        Meter.Id id = jvmInfo.getId();
        assertThat(id.getTag("version")).isNotNull();
        assertThat(id.getTag("vendor")).isNotNull();
        assertThat(id.getTag("runtime")).isNotNull();
    }

}
