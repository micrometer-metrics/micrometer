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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.jspecify.annotations.Nullable;

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

    private final Duration delay;

    private Consumer<HighCardinalityMeterInfo> meterInfoConsumer;

    private final ScheduledExecutorService scheduledExecutorService;

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
     * @deprecated Use {@link Builder#highCardinalityMeterInfoConsumer(Consumer)} instead.
     */
    @Deprecated
    public HighCardinalityTagsDetector(MeterRegistry registry, long threshold, Duration delay,
            @Nullable Consumer<String> meterNameConsumer) {
        this.registry = registry;
        this.threshold = threshold;
        this.delay = delay;
        if (meterNameConsumer != null) {
            this.meterInfoConsumer = (meterInfo) -> meterNameConsumer.accept(meterInfo.getName());
        }
        else {
            this.meterInfoConsumer = this::logWarning;
        }
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
            findFirstHighCardinalityMeterInfo().ifPresent(this.meterInfoConsumer);
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
        return findFirstHighCardinalityMeterInfo().map(HighCardinalityMeterInfo::getName);
    }

    /**
     * Finds the {@code HighCardinalityMeterInfo} of the first Meter that potentially has
     * high cardinality tags.
     * @return the {@code HighCardinalityMeterInfo} of the first Meter that potentially
     * has high cardinality tags, or an empty Optional if none found.
     * @since 1.16.0
     */
    public Optional<HighCardinalityMeterInfo> findFirstHighCardinalityMeterInfo() {
        Map<String, Long> meterNameFrequencies = new LinkedHashMap<>();
        for (Meter meter : this.registry.getMeters()) {
            meterNameFrequencies.compute(meter.getId().getName(), (k, v) -> v == null ? 1 : v + 1);
        }
        return meterNameFrequencies.entrySet()
            .stream()
            .filter((entry) -> entry.getValue() > this.threshold)
            .map((entry) -> new HighCardinalityMeterInfo(entry.getKey(), entry.getValue()))
            .findFirst();
    }

    private void logWarning(HighCardinalityMeterInfo meterInfo) {
        WARN_THEN_DEBUG_LOGGER.log(() -> {
            String name = meterInfo.getName();
            return String.format("%s has %d meters, which seems to have high cardinality tags (threshold: %d meters).\n"
                    + "Check your configuration for the instrumentation of %s to find and fix the cause of the high cardinality (see: https://docs.micrometer.io/micrometer/reference/concepts/naming.html#_tag_values).\n"
                    + "If the cardinality is expected and acceptable, raise the threshold for this %s.", name,
                    meterInfo.getCount(), this.threshold, name, getClass().getSimpleName());
        });
    }

    private static long calculateThreshold() {
        // 10% of the heap in MiB
        long allowance = Runtime.getRuntime().maxMemory() / 1024 / 1024 / 10;

        // 2k Meters can take ~1MiB, 2M Meters can take ~1GiB
        return Math.max(1_000, Math.min(allowance * 2_000, 2_000_000));
    }

    /**
     * Builder for {@code HighCardinalityTagsDetector}.
     */
    public static class Builder {

        private final MeterRegistry registry;

        private long threshold = calculateThreshold();

        private Duration delay = DEFAULT_DELAY;

        private @Nullable Consumer<HighCardinalityMeterInfo> highCardinalityMeterInfoConsumer;

        /**
         * Create a {@code Builder}.
         * @param registry registry
         */
        public Builder(MeterRegistry registry) {
            this.registry = registry;
        }

        /**
         * Set threshold.
         * @param threshold threshold
         * @return this builder
         */
        public Builder threshold(long threshold) {
            this.threshold = threshold;
            return this;
        }

        /**
         * Set delay.
         * @param delay delay
         * @return this builder
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Set {@code HighCardinalityMeterInfo} {@link Consumer}.
         * @param highCardinalityMeterInfoConsumer {@code HighCardinalityMeterInfo}
         * {@code Consumer}
         * @return this builder
         */
        public Builder highCardinalityMeterInfoConsumer(
                Consumer<HighCardinalityMeterInfo> highCardinalityMeterInfoConsumer) {
            this.highCardinalityMeterInfoConsumer = highCardinalityMeterInfoConsumer;
            return this;
        }

        /**
         * Build {@code HighCardinalityTagsDetector}.
         * @return {@code HighCardinalityTagsDetector}
         */
        public HighCardinalityTagsDetector build() {
            HighCardinalityTagsDetector highCardinalityTagsDetector = new HighCardinalityTagsDetector(this.registry,
                    this.threshold, this.delay);
            if (this.highCardinalityMeterInfoConsumer != null) {
                highCardinalityTagsDetector.meterInfoConsumer = this.highCardinalityMeterInfoConsumer;
            }
            return highCardinalityTagsDetector;
        }

    }

    /**
     * High cardinality meter information.
     */
    public static class HighCardinalityMeterInfo {

        private final String name;

        private final long count;

        /**
         * Create a {@code HighCardinalityMeterInfo} instance.
         * @param name name
         * @param count count
         */
        public HighCardinalityMeterInfo(String name, long count) {
            this.name = name;
            this.count = count;
        }

        /**
         * Return the name.
         * @return name
         */
        public String getName() {
            return this.name;
        }

        /**
         * Return the count.
         * @return count
         */
        public long getCount() {
            return this.count;
        }

    }

}
