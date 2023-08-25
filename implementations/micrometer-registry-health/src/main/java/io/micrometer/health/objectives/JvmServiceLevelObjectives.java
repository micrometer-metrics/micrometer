/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health.objectives;

import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.health.ServiceLevelObjective;

import java.time.Duration;

/**
 * {@link ServiceLevelObjective ServiceLevelObjectives} for Java Virtual Machine.
 *
 * @author Jon Schneider
 * @since 1.6.0
 */
public class JvmServiceLevelObjectives {

    /**
     * A series of high-level heap monitors originally defined in
     * <a href="https://www.jetbrains.com/help/teamcity/teamcity-memory-monitor.html">Team
     * City's memory monitor</a>.
     */
    public static final ServiceLevelObjective[] MEMORY = new ServiceLevelObjective[] {
            ServiceLevelObjective.build("jvm.pool.memory")
                .failedMessage("Memory usage in a single memory pool exceeds 90% after garbage collection.")
                .requires(new JvmHeapPressureMetrics())
                .baseUnit("percent used")
                .value(s -> s.name("jvm.memory.usage.after.gc"))
                .isLessThan(0.9),

            ServiceLevelObjective.build("jvm.gc.load")
                .failedMessage("Memory cleaning is taking more than 50% of CPU resources on average. "
                        + "This usually means really serious problems with memory resulting in high performance degradation.")
                .requires(new JvmHeapPressureMetrics())
                .baseUnit("percent CPU time spent")
                .value(s -> s.name("jvm.gc.overhead"))
                .isLessThan(0.5),

            ServiceLevelObjective
                .compose("jvm.total.memory",
                        ServiceLevelObjective.build("jvm.gc.overhead")
                            .failedMessage("More than 20% of CPU resources are being consumed by garbage collection.")
                            .baseUnit("percent CPU time spent")
                            .requires(new JvmHeapPressureMetrics())
                            .value(s -> s.name("jvm.gc.overhead"))
                            .isLessThan(0.2),
                        ServiceLevelObjective.build("jvm.memory.consumption")
                            .failedMessage("More than 90% of total memory has been in use during the last 5 minutes.")
                            .baseUnit("maximum percent used in last 5 minutes")
                            .requires(new JvmMemoryMetrics())
                            .value(s -> s.name("jvm.memory.used"))
                            .dividedBy(denom -> denom.value(s -> s.name("jvm.memory.committed")))
                            .maxOver(Duration.ofMinutes(5))
                            .isLessThan(0.9))
                .failedMessage(
                        "More than 90% of total memory has been in use during the last 5 minutes and more than 20% of CPU resources are being consumed by garbage collection. "
                                + "Lasting memory lack may result in performance degradation and server instability.")
                .and() };

    public static final ServiceLevelObjective[] ALLOCATIONS = new ServiceLevelObjective[] {
            ServiceLevelObjective.build("jvm.allocations.g1.humongous")
                .failedMessage("A single object was allocated that exceeded 50% of the total size of the eden space.")
                .baseUnit("allocations")
                .requires(new JvmGcMetrics())
                .count(s -> s.name("jvm.gc.pause").tag("cause", "G1 Humongous Allocation"))
                .isEqualTo(0) };

    private JvmServiceLevelObjectives() {
    }

}
