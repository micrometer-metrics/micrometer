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

import java.util.Random;
import java.util.concurrent.*;

public abstract class PushMeterRegistry extends MeterRegistry {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);

    // Schedule publishing in the beginning X percent of the step to avoid spill-over into
    // the next step.
    private static final double PERCENT_RANGE_OF_RANDOM_PUBLISHING_OFFSET = 0.8;

    private final PushRegistryConfig config;

    private final Semaphore publishingSemaphore = new Semaphore(1);

    private long lastScheduledPublishStartTime = 0L;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected PushMeterRegistry(PushRegistryConfig config, Clock clock) {
        super(clock);

        config.requireValid();

        this.config = config;
    }

    protected abstract void publish();

    /**
     * Catch uncaught exceptions thrown from {@link #publish()}. Skip publishing if
     * another call to this method is already in progress.
     */
    // VisibleForTesting
    void publishSafelyOrSkipIfInProgress() {
        if (this.publishingSemaphore.tryAcquire()) {
            this.lastScheduledPublishStartTime = clock.wallTime();
            try {
                publish();
            }
            catch (Throwable e) {
                logger.warn("Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(),
                        e);
            }
            finally {
                this.publishingSemaphore.release();
            }
        }
        else {
            logger.warn("Publishing is already in progress. Skipping duplicate call to publish().");
        }
    }

    /**
     * Returns if scheduled publishing of metrics is in progress.
     * @return if scheduled publishing of metrics is in progress
     * @since 1.11.0
     */
    protected boolean isPublishing() {
        return publishingSemaphore.availablePermits() == 0;
    }

    /**
     * Returns the time, in milliseconds, when the last scheduled publish was started by
     * {@link PushMeterRegistry#publishSafelyOrSkipIfInProgress()}.
     * @since 1.11.1
     */
    protected long getLastScheduledPublishStartTime() {
        return lastScheduledPublishStartTime;
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
            logger.info(startMessage());

            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            long stepMillis = config.step().toMillis();
            long initialDelayMillis = calculateInitialDelay();
            scheduledExecutorService.scheduleAtFixedRate(this::publishSafelyOrSkipIfInProgress, initialDelayMillis,
                    stepMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Message that will be logged when the registry is {@link #start(ThreadFactory)
     * started}. This can be overridden to customize the message with info specific to the
     * registry implementation that may be helpful in troubleshooting. By default, the
     * registry class name and step interval are included.
     * @return message to log on registry start
     * @since 1.13.0
     */
    protected String startMessage() {
        return "publishing metrics for " + getClass().getSimpleName() + " every " + TimeUtils.format(config.step());
    }

    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    @Override
    public void close() {
        stop();
        if (config.enabled() && !isClosed()) {
            // do a final publish on close or wait for the in progress scheduled publish
            publishSafelyOrSkipIfInProgress();
            waitForInProgressScheduledPublish();
        }
        super.close();
    }

    /**
     * Wait until scheduled publishing by {@link PushMeterRegistry} completes, if in
     * progress.
     * @since 1.11.6
     */
    protected void waitForInProgressScheduledPublish() {
        try {
            // block until in progress publish finishes
            publishingSemaphore.acquire();
            publishingSemaphore.release();
        }
        catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for publish on close to finish", e);
        }
    }

    // VisibleForTesting
    long calculateInitialDelay() {
        long stepMillis = config.step().toMillis();
        Random random = new Random();
        // in range of [0, X% of step - 2)
        long randomOffsetWithinStep = Math.max(0,
                (long) (stepMillis * random.nextDouble() * PERCENT_RANGE_OF_RANDOM_PUBLISHING_OFFSET) - 2);
        long offsetToStartOfNextStep = stepMillis - (clock.wallTime() % stepMillis);
        // at least 2ms into step, so it is after StepMeterRegistry's meterPollingService
        return offsetToStartOfNextStep + 2 + randomOffsetWithinStep;
    }

}
