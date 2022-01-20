/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.api.instrument.push;

import io.micrometer.api.instrument.Clock;
import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.util.TimeUtils;
import io.micrometer.api.lang.Nullable;
import io.micrometer.api.internal.logging.InternalLogger;
import io.micrometer.api.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class PushMeterRegistry extends MeterRegistry {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);
    private final PushRegistryConfig config;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected PushMeterRegistry(PushRegistryConfig config, Clock clock) {
        super(clock);

        config.requireValid();

        this.config = config;
    }

    protected abstract void publish();

    /**
     * Catch uncaught exceptions thrown from {@link #publish()}.
     */
    private void publishSafely() {
        try {
            publish();
        } catch (Throwable e) {
            logger.warn("Unexpected exception thrown while publishing metrics for " + this.getClass().getSimpleName(), e);
        }
    }

    /**
     * @deprecated Use {@link #start(ThreadFactory)} instead.
     */
    @Deprecated
    public final void start() {
        start(Executors.defaultThreadFactory());
    }

    public void start(ThreadFactory threadFactory) {
        if (scheduledExecutorService != null)
            stop();

        if (config.enabled()) {
            logger.info("publishing metrics for " + this.getClass().getSimpleName() + " every " + TimeUtils.format(config.step()));

            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            // time publication to happen just after StepValue finishes the step
            long stepMillis = config.step().toMillis();
            long initialDelayMillis = stepMillis - (clock.wallTime() % stepMillis) + 1;
            scheduledExecutorService.scheduleAtFixedRate(this::publishSafely,
                                                         initialDelayMillis, stepMillis, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    @Override
    public void close() {
        if (config.enabled()) {
            publishSafely();
        }
        stop();
        super.close();
    }
}
