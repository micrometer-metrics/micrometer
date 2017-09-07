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
package io.micrometer.core.instrument.binder;

import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Uptime metrics.
 *
 * @author Michael Weirauch
 */
public class UptimeMetrics implements MeterBinder {

    private final RuntimeMXBean runtimeMXBean;

    public UptimeMetrics() {
        this(ManagementFactory.getRuntimeMXBean());
    }

    // VisibleForTesting
    UptimeMetrics(RuntimeMXBean runtimeMXBean) {
        this.runtimeMXBean = requireNonNull(runtimeMXBean);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("uptime", runtimeMXBean, (x) -> Long.valueOf(x.getUptime()).doubleValue());
    }

}
