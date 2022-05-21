/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;


/**
 * Tries to detect high cardinality tags by checking if the amount of Meters with the same name is above a threshold.
 * You can use this class in two ways:
 *   1. Call findFirst and check if you get any results, if so you probably have high cardinality tags
 *   2. Call start which will start a scheduled job that will do this check for you
 *
 * @author Jonatan Ivanov
 * @since 1.9.0
 */
public class HighCardinalityTagsDetector implements AutoCloseable {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(HighCardinalityTagsDetector.class);

    private final MeterRegistry registry;
    private final int threshold;
    private final Consumer<String> meterNameConsumer;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Duration delay;

    /**
     * @param registry The registry to use to check the Meters in it
     */
    public HighCardinalityTagsDetector(MeterRegistry registry) {
        this(registry, 1_000_000);
    }

    /**
     * @param registry The registry to use to check the Meters in it
     * @param threshold The threshold to use to detect high cardinality tags
     *                  (if the number of Meters with the same name are higher than this value, that's a high cardinality tag)
     */
    public HighCardinalityTagsDetector(MeterRegistry registry, int threshold) {
        this(registry, threshold, HighCardinalityTagsDetector::logWarning);
    }

    /**
     * @param registry The registry to use to check the Meters in it
     * @param threshold The threshold to use to detect high cardinality tags
     *                  (if the number of Meters with the same name are higher than this value, that's a high cardinality tag)
     * @param meterNameConsumer The action to execute if the first high cardinality tag is found
     */
    public HighCardinalityTagsDetector(MeterRegistry registry, int threshold, Consumer<String> meterNameConsumer) {
        this.registry = registry;
        this.threshold = threshold;
        this.meterNameConsumer = meterNameConsumer;
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.delay = Duration.ofMinutes(5);
    }

    /**
     * Starts a scheduled job that checks if you have high cardinality tags.
     */
    public void start() {
        this.scheduledExecutorService.scheduleWithFixedDelay(
                this::detectHighCardinalityTags,
                0,
                this.delay.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Shuts down the scheduled job that checks if you have high cardinality tags.
     */
    public void shutdown() {
        this.scheduledExecutorService.shutdown();
    }

    private void detectHighCardinalityTags() {
        try {
            findFirst().ifPresent(this.meterNameConsumer);
        }
        catch (Exception exception) {
            LOGGER.warn("Something went wrong during high cardinality tag detection", exception);
        }
    }

    /**
     * Finds the name of the first Meter that potentially has high cardinality tags.
     *
     * @return the name of the first Meter that potentially has high cardinality tags, an empty Optional if none found.
     */
    public Optional<String> findFirst() {
        Map<String, Integer> meterNameFrequencies = new HashMap<>();
        for (Meter meter : this.registry.getMeters()) {
            String name = meter.getId().getName();
            if (!meterNameFrequencies.containsKey(name)) {
                meterNameFrequencies.put(name, 1);
            }
            else {
                Integer frequency = meterNameFrequencies.get(name);
                if (frequency < this.threshold) {
                    meterNameFrequencies.put(name, frequency + 1);
                }
                else {
                    return Optional.of(name);
                }
            }
        }

        return Optional.empty();
    }

    private static void logWarning(String name) {
        LOGGER.warn(String.format("It seems %s has high cardinality tags.", name));
    }

    @Override
    public void close() {
        this.scheduledExecutorService.shutdown();
    }
}
