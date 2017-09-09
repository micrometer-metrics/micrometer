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
import io.micrometer.core.instrument.Tag;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import static java.util.Collections.emptyList;

public class JvmThreadMetrics implements MeterBinder {
    private final Iterable<Tag> tags;

    public JvmThreadMetrics() {
        this(emptyList());
    }

    public JvmThreadMetrics(Iterable<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        registry.gauge(registry.createId("jvm.threads.peak", tags,
            "the peak live thread count since the Java virtual machine started or peak was reset"),
            threadBean, ThreadMXBean::getPeakThreadCount);

        registry.gauge(registry.createId("jvm.threads.daemon", tags,
            "The current number of live daemon threads"),
            threadBean, ThreadMXBean::getDaemonThreadCount);

        registry.gauge(registry.createId("jvm.threads.live", tags,
            "The current number of live threads including both daemon and non-daemon threads"),
            threadBean, ThreadMXBean::getThreadCount);
    }
}
