/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.ehcache.search.expression.IsNull;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ProcessorMetricsTest {
    @Test
    void cpuMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new ProcessorMetrics().bindTo(registry);

        assertThat(registry.mustFind("system.cpu.count").gauge().value()).isGreaterThan(0);
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertThat(registry.find("system.load.average.1m").gauge())
                .describedAs("Not present on windows").isNull();
        } else {
            assertThat(registry.mustFind("system.load.average.1m").gauge().value()).isGreaterThan(0);
        }
    }
}
