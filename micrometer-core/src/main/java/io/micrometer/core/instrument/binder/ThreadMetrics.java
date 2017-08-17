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

import io.micrometer.core.instrument.MeterRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class ThreadMetrics implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        registry.gauge("threads.peak", threadBean, ThreadMXBean::getPeakThreadCount);
        registry.gauge("threads.daemon", threadBean, ThreadMXBean::getDaemonThreadCount);
        registry.gauge("threads.live", threadBean, ThreadMXBean::getThreadCount);
    }
}
