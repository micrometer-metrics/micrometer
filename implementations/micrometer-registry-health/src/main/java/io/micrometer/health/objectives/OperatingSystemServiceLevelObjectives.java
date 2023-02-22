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

import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.health.ServiceLevelObjective;

/**
 * {@link ServiceLevelObjective ServiceLevelObjectives} for Operating System.
 *
 * @author Jon Schneider
 * @since 1.6.0
 */
public class OperatingSystemServiceLevelObjectives {

    public static final ServiceLevelObjective[] DISK = new ServiceLevelObjective[] {
            ServiceLevelObjective.build("os.file.descriptors")
                .failedMessage("Too many file descriptors are open. When the max is reached, "
                        + "further attempts to retrieve a file descriptor will block indefinitely.")
                .baseUnit("used / available (percent)")
                .requires(new FileDescriptorMetrics())
                .value(s -> s.name("process.open.fds"))
                .dividedBy(denom -> denom.value(s -> s.name("process.max.fds")))
                .isLessThan(0.8) };

    private OperatingSystemServiceLevelObjectives() {
    }

}
