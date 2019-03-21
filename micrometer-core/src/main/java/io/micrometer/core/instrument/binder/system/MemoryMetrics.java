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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

import java.util.Collections;

/**
 * Collects physical memory (RAM) metrics from host.
 *
 * For detailed info, checkout https://github.com/oshi/oshi.
 *
 * @author Johan Rask
 */
public class MemoryMetrics implements MeterBinder {

    private final GlobalMemory memory;
    private final Iterable<Tag> tags;

    public MemoryMetrics() {
        this(Collections.emptyList());
    }

    public MemoryMetrics(Iterable<Tag> tags) {
        this.memory = new SystemInfo().getHardware().getMemory();

        this.tags = tags;
    }

    public void bindTo(MeterRegistry meterRegistry) {

        Gauge.builder("system.memory.available", () -> memory.getAvailable())
            .tags(tags)
            .baseUnit("bytes")
            .description("Memory available")
            .register(meterRegistry);


        Gauge.builder("system.memory.total", () -> memory.getTotal())
            .tags(tags)
            .baseUnit("bytes")
            .description("Memory available")
            .register(meterRegistry);


        Gauge.builder("system.memory.used", () -> memory.getTotal() - memory.getAvailable())
            .tags(tags)
            .baseUnit("bytes")
            .description("Memory available")

            .register(meterRegistry);

        Gauge.builder("system.memory.swap.total", () -> memory.getSwapTotal())
            .tags(tags)
            .baseUnit("bytes")
            .description("Memory Swap total")
            .register(meterRegistry);

        Gauge.builder("system.memory.swap.used", () -> memory.getSwapUsed())
            .tags(tags)
            .baseUnit("bytes")
            .description("Memory Swap used")
            .register(meterRegistry);

    }


}
