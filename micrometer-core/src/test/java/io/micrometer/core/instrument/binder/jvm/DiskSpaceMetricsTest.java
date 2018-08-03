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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link DiskSpaceMetrics}.
 *
 * @author jmcshane
 * @author Johnny Lim
 */
class DiskSpaceMetricsTest {
    @Test
    void diskSpaceMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new DiskSpaceMetrics(new File(System.getProperty("user.dir"))).bindTo(registry);

        assertThat(registry.get("disk.free").gauge().value()).isGreaterThan(0);
        assertThat(registry.get("disk.total").gauge().value()).isGreaterThan(0);
    }

    @Test
    void diskSpaceMetricsWithTags() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new DiskSpaceMetrics(new File(System.getProperty("user.dir")), Tags.of("key1", "value1")).bindTo(registry);

        assertThat(registry.get("disk.free").tags("key1", "value1").gauge().value()).isGreaterThan(0);
        assertThat(registry.get("disk.total").tags("key1", "value1").gauge().value()).isGreaterThan(0);
    }

}
