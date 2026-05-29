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
package io.micrometer.core.instrument.binder.jvm;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmThreadCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.JvmThreadMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmThreadCountMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadCountMeterConvention;
import org.jspecify.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

import static java.util.Collections.emptyList;

/**
 * {@link MeterBinder} for JVM threads.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class JvmThreadMetrics implements MeterBinder {

    private final Tags extraTags;

    private final JvmThreadCountMeterConvention threadCountConvention;

    public JvmThreadMetrics() {
        this(emptyList());
    }

    /**
     * Uses the default convention with the provided extra tags.
     * @param extraTags tags to add to each meter's tags produced by this binder
     */
    public JvmThreadMetrics(Iterable<Tag> extraTags) {
        this(Tags.of(extraTags), new MicrometerJvmThreadCountMeterConvention());
    }

    /**
     * The supplied extra tags are not combined with the convention. Provide a convention
     * that applies the extra tags if that is the desired outcome. The convention only
     * applies to some meters.
     * @param extraTags extra tags to add to meters not covered by the conventions
     * @param conventions custom conventions for applicable meters
     * @since 1.16.0
     * @deprecated use {@link #builder()} to provide individual conventions
     */
    @Deprecated
    public JvmThreadMetrics(Iterable<? extends Tag> extraTags, JvmThreadMeterConventions conventions) {
        this.extraTags = Tags.of(extraTags);
        MeterConvention<Thread.State> threadCount = conventions.threadCountConvention();
        this.threadCountConvention = JvmThreadCountMeterConvention.of(threadCount.getName(), threadCount::getTags);
    }

    private JvmThreadMetrics(Tags extraTags, JvmThreadCountMeterConvention threadCountConvention) {
        this.extraTags = extraTags;
        this.threadCountConvention = threadCountConvention;
    }

    /**
     * Create a new builder for {@link JvmThreadMetrics}.
     * @return a new builder
     * @since 1.16.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        Gauge.builder("jvm.threads.peak", threadBean, ThreadMXBean::getPeakThreadCount)
            .tags(extraTags)
            .description("The peak live thread count since the Java virtual machine started or peak was reset")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        Gauge.builder("jvm.threads.daemon", threadBean, ThreadMXBean::getDaemonThreadCount)
            .tags(extraTags)
            .description("The current number of live daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        Gauge.builder("jvm.threads.live", threadBean, ThreadMXBean::getThreadCount)
            .tags(extraTags)
            .description("The current number of live threads including both daemon and non-daemon threads")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        FunctionCounter.builder("jvm.threads.started", threadBean, ThreadMXBean::getTotalStartedThreadCount)
            .tags(extraTags)
            .description("The total number of application threads started in the JVM")
            .baseUnit(BaseUnits.THREADS)
            .register(registry);

        try {
            threadBean.getAllThreadIds();
            for (Thread.State state : Thread.State.values()) {
                Gauge
                    .builder(threadCountConvention.getName(), threadBean,
                            (bean) -> getThreadStateCount(bean, state))
                    .tags(threadCountConvention.getTags(state))
                    .tags(extraTags)
                    .description("The current number of threads")
                    .baseUnit(BaseUnits.THREADS)
                    .register(registry);
            }
        }
        catch (Error error) {
            // An error will be thrown for unsupported operations
            // e.g. SubstrateVM does not support getAllThreadIds
        }
    }

    // VisibleForTesting
    static long getThreadStateCount(ThreadMXBean threadBean, Thread.State state) {
        return Arrays.stream(threadBean.getThreadInfo(threadBean.getAllThreadIds()))
            .filter(threadInfo -> threadInfo != null && threadInfo.getThreadState() == state)
            .count();
    }

    /**
     * Builder for {@link JvmThreadMetrics}.
     *
     * @since 1.16.0
     */
    public static class Builder {

        private Tags extraTags = Tags.empty();

        private @Nullable JvmThreadCountMeterConvention threadCountConvention;

        Builder() {
        }

        /**
         * Extra tags to add to meters registered by this binder.
         * @param extraTags tags to add
         * @return this builder
         */
        public Builder extraTags(Iterable<? extends Tag> extraTags) {
            this.extraTags = Tags.of(extraTags);
            return this;
        }

        /**
         * Custom convention for the thread count meter.
         * @param convention the convention to use
         * @return this builder
         */
        public Builder threadCountConvention(JvmThreadCountMeterConvention convention) {
            this.threadCountConvention = convention;
            return this;
        }

        /**
         * Use OpenTelemetry semantic conventions for all meters. Individual conventions
         * can still be overridden by calling the specific convention methods after this
         * one.
         * @return this builder
         */
        public Builder openTelemetryConventions() {
            this.threadCountConvention = new OpenTelemetryJvmThreadCountMeterConvention();
            return this;
        }

        /**
         * Build a new {@link JvmThreadMetrics} instance.
         * @return a new {@link JvmThreadMetrics}
         */
        public JvmThreadMetrics build() {
            return new JvmThreadMetrics(extraTags, threadCountConvention != null ? threadCountConvention
                    : new MicrometerJvmThreadCountMeterConvention());
        }

    }

}
