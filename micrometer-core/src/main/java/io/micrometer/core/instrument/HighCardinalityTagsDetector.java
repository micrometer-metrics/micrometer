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

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.util.NamedThreadFactory;

/**
 * Tries to detect high cardinality tags by checking if the amount of Meters with the same
 * name is above a threshold. This mechanism will not detect if you have other
 * memory-usage-related issues, like appending random values to the name of the Meters,
 * the only purpose of this class is detecting the potential presence of high cardinality
 * tags. You can use this class in two ways:
 *
 * <ul>
 * <li>Call findFirst and check if you get any results, if so you probably have high
 * cardinality tags</li>
 * <li>Call start which will start a scheduled job that will do this check for you.</li>
 * </ul>
 *
 * You can also utilize
 * {@link MeterFilter#maximumAllowableTags(String, String, int, MeterFilter)} and
 * {@link MeterFilter#maximumAllowableMetrics(int)} to set an upper bound on the number of
 * tags/metrics.
 *
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
public class HighCardinalityTagsDetector implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(HighCardinalityTagsDetector.class);

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(
            HighCardinalityTagsDetector.class);

    private static final Duration DEFAULT_DELAY = Duration.ofMinutes(5);

    private final MeterRegistry registry;

    private final long threshold;

    private final Consumer<String> meterNameConsumer;

    private final ScheduledExecutorService scheduledExecutorService;

    private final Duration delay;

    /**
     * @param registry The registry to use to check the Meters in it
     */
    public HighCardinalityTagsDetector(MeterRegistry registry) {
        this(registry, calculateThreshold(), DEFAULT_DELAY);
    }

    /**
     * @param registry The registry to use to check the Meters in it
     * @param threshold The threshold to use to detect high cardinality tags (if the
     * number of Meters with the same name is higher than this value, that's a high
     * cardinality tag)
     * @param delay The delay between the termination of one check and the commencement of
     * the next
     */
    public HighCardinalityTagsDetector(MeterRegistry registry, long threshold, Duration delay) {
        this(registry, threshold, delay, null);
    }

    /**
     * @param registry The registry to use to check the Meters in it
     * @param threshold The threshold to use to detect high cardinality tags (if the
     * number of Meters with the same name is higher than this value, that's a high
     * cardinality tag)
     * @param delay The delay between the termination of one check and the commencement of
     * the next
     * @param meterNameConsumer The action to execute if the first high cardinality tag is
     * found
     */
    public HighCardinalityTagsDetector(MeterRegistry registry, long threshold, Duration delay,
            @Nullable Consumer<String> meterNameConsumer) {
        this.registry = registry;
        this.threshold = threshold;
        this.delay = delay;
        this.meterNameConsumer = meterNameConsumer != null ? meterNameConsumer : this::logWarning;
        this.scheduledExecutorService = Executors
            .newSingleThreadScheduledExecutor(new NamedThreadFactory("high-cardinality-tags-detector"));
    }

    /**
     * Starts a scheduled job that checks if you have high cardinality tags.
     */
    public void start() {
        LOGGER.info(String.format("Starting %s with threshold: %d and delay: %s", getClass().getSimpleName(),
                this.threshold, this.delay));
        this.scheduledExecutorService.scheduleWithFixedDelay(this::detectHighCardinalityTags, 0, this.delay.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Shuts down the scheduled job that checks if you have high cardinality tags.
     */
    public void shutdown() {
        LOGGER.info("Stopping " + getClass().getSimpleName());
        this.scheduledExecutorService.shutdown();
    }

    @Override
    public void close() {
        shutdown();
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
     * @return the name of the first Meter that potentially has high cardinality tags, an
     * empty Optional if none found.
     */
    public Optional<String> findFirst() {
        Map<String, Long> meterNameFrequencies = new HashMap<>();
        for (Meter meter : this.registry.getMeters()) {
            String name = meter.getId().getName();
            if (!meterNameFrequencies.containsKey(name)) {
                meterNameFrequencies.put(name, 1L);
            }
            else {
                Long frequency = meterNameFrequencies.get(name);
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

    private void logWarning(String name) {
        WARN_THEN_DEBUG_LOGGER.log(() -> String.format("It seems %s has high cardinality tags (threshold: %d meters).\n"
                + "Check your configuration for the instrumentation of %s to find and fix the cause of the high cardinality (see: https://docs.micrometer.io/micrometer/reference/concepts/naming.html#_tag_values).\n"
                + "If the cardinality is expected and acceptable, raise the threshold for this %s.", name,
                this.threshold, name, getClass().getSimpleName()));
    }

    private static long calculateThreshold() {
        // 10% of the heap in MiB
        long allowance = Runtime.getRuntime().maxMemory() / 1024 / 1024 / 10;

        // 2k Meters can take ~1MiB, 2M Meters can take ~1GiB
        return Math.max(1_000, Math.min(allowance * 2_000, 2_000_000));
    }

}
