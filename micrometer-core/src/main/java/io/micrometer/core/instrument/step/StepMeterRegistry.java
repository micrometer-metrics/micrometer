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
package io.micrometer.core.instrument.step;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Registry that step-normalizes counts and sums to a rate/second over the publishing
 * interval.
 *
 * @author Jon Schneider
 */
public abstract class StepMeterRegistry extends PushMeterRegistry {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(StepMeterRegistry.class);

    private final StepRegistryConfig config;

    @Nullable
    private ScheduledExecutorService meterPollingService;

    // Time when the last scheduled rollOver has started.
    private volatile long lastMeterRolloverStartTime = -1;

    public StepMeterRegistry(StepRegistryConfig config, Clock clock) {
        super(config, clock);
        this.config = config;
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StepCounter(id, clock, config.step().toMillis());
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig, false);
        HistogramGauges.registerWithCommonFormat(ltt, this);
        return ltt;
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
        Timer timer = new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                this.config.step().toMillis(), false);
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id,
            DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionSummary summary = new StepDistributionSummary(id, clock, distributionStatisticConfig, scale,
                config.step().toMillis(), false);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction,
            ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(config.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        super.start(threadFactory);

        if (config.enabled()) {
            this.meterPollingService = Executors.newSingleThreadScheduledExecutor(
                    new NamedThreadFactory("step-meter-registry-poller-for-" + getClass().getSimpleName()));
            this.meterPollingService.scheduleAtFixedRate(this::pollMetersToRollover, getInitialDelay(),
                    config.step().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (this.meterPollingService != null) {
            this.meterPollingService.shutdown();
        }
    }

    @Override
    public void close() {
        stop();

        if (config.enabled() && !isClosed()) {
            if (shouldPublishDataForLastStep() && !isPublishing()) {
                // Data was not published for the last completed step. So, we should flush
                // that first.
                try {
                    publish();
                }
                catch (Throwable e) {
                    logger.warn(
                            "Unexpected exception thrown while publishing metrics for " + getClass().getSimpleName(),
                            e);
                }
            }
            else if (isPublishing()) {
                waitForInProgressScheduledPublish();
            }
            closingRolloverStepMeters();
        }
        super.close();
    }

    private boolean shouldPublishDataForLastStep() {
        if (lastMeterRolloverStartTime < 0)
            return false;

        final long lastPublishedStep = getLastScheduledPublishStartTime() / config.step().toMillis();
        final long lastPolledStep = lastMeterRolloverStartTime / config.step().toMillis();
        return lastPublishedStep < lastPolledStep;
    }

    /**
     * Performs closing rollover on StepMeters.
     */
    private void closingRolloverStepMeters() {
        getMeters().stream()
            .filter(StepMeter.class::isInstance)
            .map(StepMeter.class::cast)
            .forEach(StepMeter::_closingRollover);
    }

    /**
     * This will poll the values from meters, which will cause a roll over for Step-meters
     * if past the step boundary. This gives some control over when roll over happens
     * separate from when publishing happens.
     */
    // VisibleForTesting
    void pollMetersToRollover() {
        this.lastMeterRolloverStartTime = clock.wallTime();
        this.getMeters()
            .forEach(m -> m.match(gauge -> null, Counter::count, Timer::count, DistributionSummary::count,
                    meter -> null, meter -> null, FunctionCounter::count, FunctionTimer::count, meter -> null));
    }

    private long getInitialDelay() {
        long stepMillis = config.step().toMillis();
        // schedule one millisecond into the next step
        return stepMillis - (clock.wallTime() % stepMillis) + 1;
    }

}
