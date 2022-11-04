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
package io.micrometer.core.instrument.push;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class PushMeterRegistry extends MeterRegistry {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);

    private final PushRegistryConfig config;

    private final long registryCreationOffsetFromEpochStepMillis;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected PushMeterRegistry(PushRegistryConfig config, Clock clock) {
        super(clock);

        config.requireValid();

        this.config = config;
        this.registryCreationOffsetFromEpochStepMillis = clock.wallTime() % this.config.step().toMillis();
    }

    protected abstract void publish();

    /**
     * Catch uncaught exceptions thrown from {@link #publish()}.
     */
    private void publishSafely() {
        try {
            publish();
        }
        catch (Throwable e) {
            logger.warn("Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(), e);
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
            logger.info("publishing metrics for " + getClass().getSimpleName() + " every "
                    + TimeUtils.format(config.step()));

            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            // time publication to happen just after StepValue finishes the step
            long stepMillis = config.step().toMillis();
            long initialDelayMillis = calculateInitialDelayMillis();

            scheduledExecutorService.scheduleAtFixedRate(this::publishSafely, initialDelayMillis, stepMillis,
                    TimeUnit.MILLISECONDS);
        }
    }

    // VisibleForTesting
    long calculateInitialDelayMillis() {
        long stepMillis = config.step().toMillis();
        long initialDelayMillis;
        if (config.alignToEpoch()) {
            initialDelayMillis = stepMillis - (clock.wallTime() % stepMillis) + 1;
        }
        else {
            initialDelayMillis = stepMillis
                    - ((clock.wallTime() - registryCreationOffsetFromEpochStepMillis) % stepMillis) + 1;
        }
        return initialDelayMillis;
    }

    protected long getOffsetFromEpochStepMillis() {
        return config.alignToEpoch() ? 0 : registryCreationOffsetFromEpochStepMillis;
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
