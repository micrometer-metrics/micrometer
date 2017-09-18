/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.spectator.step;

import com.netflix.spectator.api.*;
import com.netflix.spectator.impl.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A {@link com.netflix.spectator.api.Registry} implementation that is suitable for
 * any monitoring system that expects metrics on an interval with step-aggregated values.
 *
 * Where applicable, the {@link com.netflix.spectator.api.Meter} implementations created
 * by this registry will maintain two values where one is the current value being updated
 * and the other is the value from the previous interval and is only available for polling.
 */
public abstract class AbstractStepRegistry extends AbstractRegistry {
    private final Clock clock;

    private final boolean enabled;
    private final Duration step;
    private final long stepMillis;

    protected final int connectTimeout;
    protected final int readTimeout;
    private final int batchSize;
    private final int numThreads;

    private Scheduler scheduler;

    public AbstractStepRegistry(StepRegistryConfig config, Clock clock) {
        super(new StepClock(clock, config.step().toMillis()), config);
        this.clock = clock;

        this.enabled = config.enabled();
        this.step = config.step();
        this.stepMillis = step.toMillis();

        this.connectTimeout = (int) config.connectTimeout().toMillis();
        this.readTimeout = (int) config.readTimeout().toMillis();
        this.batchSize = config.batchSize();
        this.numThreads = config.numThreads();
    }

    /**
     * Start the scheduler to collect metrics data.
     */
    public void start() {
        if (scheduler == null) {
            // Setup main collection for publishing
            if (enabled) {
                Scheduler.Options options = new Scheduler.Options()
                        .withFrequency(Scheduler.Policy.FIXED_RATE_SKIP_IF_LONG, step)
                        .withInitialDelay(Duration.ofMillis(getInitialDelay(stepMillis)))
                        .withStopOnFailure(false);
                scheduler = new Scheduler(this, "spring-metrics-publisher", numThreads);
                scheduler.schedule(options, this::pushMetrics);
                logger.info("started collecting metrics every {}", step);
            } else {
                logger.info("publishing is not enabled");
            }
        } else {
            logger.warn("registry already started, ignoring duplicate request");
        }
    }

    /**
     * Avoid collecting right on boundaries to minimize transitions on step longs
     * during a collection. Randomly distribute across the middle of the step interval.
     */
    private long getInitialDelay(long stepSize) {
        long now = clock.wallTime();
        long stepBoundary = now / stepSize * stepSize;

        // Buffer by 10% of the step interval on either side
        long offset = stepSize / 10;

        // Check if the current delay is within the acceptable range
        long delay = now - stepBoundary;
        if (delay < offset) {
            return delay + offset;
        } else if (delay > stepSize - offset) {
            return stepSize - offset;
        } else {
            return delay;
        }
    }

    /**
     * Stop the scheduler reporting metrics.
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            logger.info("stopped collecting metrics every {}ms", step);
        } else {
            logger.warn("registry stopped, but was never started");
        }
    }

    protected abstract void pushMetrics();

    /**
     * Get a list of all measurements from the registry.
     */
    List<Measurement> getMeasurements() {
        return stream()
                .flatMap(m -> StreamSupport.stream(m.measure().spliterator(), false))
                .collect(Collectors.toList());
    }

    /**
     * Get a list of all measurements and break them into batches.
     */
    protected List<List<Measurement>> getBatches() {
        List<List<Measurement>> batches = new ArrayList<>();
        List<Measurement> ms = getMeasurements();
        for (int i = 0; i < ms.size(); i += batchSize) {
            List<Measurement> batch = ms.subList(i, Math.min(ms.size(), i + batchSize));
            batches.add(batch);
        }
        return batches;
    }

    @Override
    protected Counter newCounter(Id id) {
        return new StepCounter(id, clock, stepMillis);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Id id) {
        return new StepDistributionSummary(id, clock, stepMillis);
    }

    @Override
    protected Timer newTimer(Id id) {
        return new StepTimer(id, clock, stepMillis);
    }

    @Override
    protected Gauge newGauge(Id id) {
        // Be sure to get StepClock so the measurements will have step aligned
        // timestamps.
        return new StepGauge(id, clock());
    }
}
