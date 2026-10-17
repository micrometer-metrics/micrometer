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

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;
import org.jspecify.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.*;

/**
 * Base class for registries that push (publish) metrics to a backend at a fixed interval.
 * <p>
 * A {@code PushMeterRegistry} has the following lifecycle:
 * <ul>
 * <li>{@link #start(ThreadFactory)} schedules {@link #publish()} to be called at a fixed
 * interval.</li>
 * <li>{@link #stop()} cancels scheduled publishing. It does not publish and it does not
 * close the registry. A stopped registry can be {@link #start(ThreadFactory) started}
 * again.</li>
 * <li>{@link #close()} stops scheduled publishing, publishes a final time so that metrics
 * recorded since the last scheduled publish are not lost, and releases the resources of
 * the registry. A closed registry cannot be started again.</li>
 * </ul>
 * Implementations that hold resources such as clients or connections should keep them
 * usable until the final publish in {@link #close()} has completed. See {@link #stop()}
 * and {@link #close()} for details on where to release which resources.
 */
public abstract class PushMeterRegistry extends MeterRegistry {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);

    // Schedule publishing in the beginning X percent of the step to avoid spill-over into
    // the next step.
    private static final double PERCENT_RANGE_OF_RANDOM_PUBLISHING_OFFSET = 0.8;

    private final PushRegistryConfig config;

    private final Semaphore publishingSemaphore = new Semaphore(1);

    private long lastScheduledPublishStartTime = 0L;

    private @Nullable ScheduledExecutorService scheduledExecutorService;

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

    /**
     * Start publishing metrics at the interval configured by
     * {@link PushRegistryConfig#step()}. If this registry is already started, it is
     * {@link #stop() stopped} and then started again. If the registry is
     * {@link PushRegistryConfig#enabled() disabled}, nothing is scheduled.
     * <p>
     * Implementations that need resources to publish (for example, a client or a
     * connection) and release them in {@link #stop()} should create them again here
     * before calling {@code super.start(threadFactory)} so that the registry can be
     * restarted after it has been stopped.
     * @param threadFactory thread factory to use for the thread that publishes metrics
     */
    @SuppressWarnings("FutureReturnValueIgnored")
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
        return "Publishing metrics for " + getClass().getSimpleName() + " every " + TimeUtils.format(config.step());
    }

    /**
     * Stop scheduled publishing of metrics. Stopping does not publish. Metrics continue
     * to be recorded while the registry is stopped, and the registry can be
     * {@link #start(ThreadFactory) started} again to resume publishing. Calling this
     * method on a registry that is not started has no effect.
     * <p>
     * This method is also called as the first step of {@link #close()}, which does a
     * final publish after stopping. Implementations overriding this method must not
     * release resources that are needed to publish; release those in {@link #close()}
     * after {@code super.close()} returns. Only resources that
     * {@link #start(ThreadFactory)} creates again should be released here.
     */
    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    /**
     * Close this registry. Scheduled publishing is {@link #stop() stopped}, then a final
     * {@link #publish()} is done (or the scheduled publish in progress is awaited) so
     * that metrics recorded since the last scheduled publish are not lost, and then the
     * registry and its meters are closed as described in {@link MeterRegistry#close()}.
     * The final publish is skipped if the registry is {@link PushRegistryConfig#enabled()
     * disabled} or already closed.
     * <p>
     * Once closed, a registry cannot be started again. Implementations that hold
     * resources such as clients or connections should release them after
     * {@code super.close()} returns so that they are still available for the final
     * publish. Calling this method on an already closed registry has no effect.
     */
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
