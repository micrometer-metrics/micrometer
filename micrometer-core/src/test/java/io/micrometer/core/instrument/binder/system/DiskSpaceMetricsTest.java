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
package io.micrometer.core.instrument.binder.system;

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
 * @author Tommy Ludwig
 */
class DiskSpaceMetricsTest {

    MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void diskSpaceMetrics() {
        // tag::example[]
        new DiskSpaceMetrics(new File(System.getProperty("user.dir"))).bindTo(registry);

        assertThat(registry.get("disk.free").gauge().value()).isNotNaN().isGreaterThan(0);
        assertThat(registry.get("disk.total").gauge().value()).isNotNaN().isGreaterThan(0);
        // end::example[]
    }

    @Test
    void diskSpaceMetricsWithTags() {
        new DiskSpaceMetrics(new File(System.getProperty("user.dir")), Tags.of("key1", "value1")).bindTo(registry);

        assertThat(registry.get("disk.free").tags("key1", "value1").gauge().value()).isNotNaN().isGreaterThan(0);
        assertThat(registry.get("disk.total").tags("key1", "value1").gauge().value()).isNotNaN().isGreaterThan(0);
    }

    @Test
    void garbageCollectionDoesNotLoseGaugeValue() {
        new DiskSpaceMetrics(new File(System.getProperty("user.dir"))).bindTo(registry);

        System.gc();

        assertThat(registry.get("disk.free").gauge().value()).isNotNaN().isGreaterThan(0);
        assertThat(registry.get("disk.total").gauge().value()).isNotNaN().isGreaterThan(0);
    }

}
