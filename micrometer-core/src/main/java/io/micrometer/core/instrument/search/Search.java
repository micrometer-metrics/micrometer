/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Search terms for locating a {@link Meter} or set of meters in a given registry based on any combination of their
 * name, tags, and type.
 *
 * @author Jon Schneider
 */
public final class Search {
    private final MeterRegistry registry;
    private final List<Tag> tags = new ArrayList<>();
    private Predicate<String> nameMatches = n -> true;
    private final Set<String> requiredTagKeys = new HashSet<>();

    private Search(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Meter contains a tag with the exact name.
     *
     * @param exactName Name to match against.
     * @return This search.
     */
    public Search name(String exactName) {
        return name(n -> n.equals(exactName));
    }

    /**
     * Meter contains a tag matching the name predicate.
     *
     * @param nameMatches Name matching predicate.
     * @return This search.
     */
    public Search name(@Nullable Predicate<String> nameMatches) {
        if (nameMatches != null)
            this.nameMatches = nameMatches;
        return this;
    }

    /**
     * Meter contains tags with the matching tag keys and values.
     *
     * @param tags The tags to match.
     * @return This search.
     */
    public Search tags(Iterable<Tag> tags) {
        tags.forEach(this.tags::add);
        return this;
    }

    /**
     * Meter contains tags with the matching tag keys and values.
     *
     * @param tags Must be an even number of arguments representing key/value pairs of tags.
     * @return This search.
     */
    public Search tags(String... tags) {
        return tags(Tags.of(tags));
    }

    /**
     * Meter contains a tag with the matching key and value.
     *
     * @param tagKey   The tag key to match.
     * @param tagValue The tag value to match.
     * @return This search.
     */
    public Search tag(String tagKey, String tagValue) {
        return tags(Tags.of(tagKey, tagValue));
    }

    /**
     * Meter contains tags with the matching keys.
     *
     * @param tagKeys The tag keys to match.
     * @return This search.
     */
    public Search tagKeys(String... tagKeys) {
        requiredTagKeys.addAll(Arrays.asList(tagKeys));
        return this;
    }

    /**
     * @return The first matching {@link Timer}, or {@code null} if none match.
     */
    @Nullable
    public Timer timer() {
        return findOne(Timer.class);
    }

    /**
     * @return The first matching {@link Counter}, or {@code null} if none match.
     */
    @Nullable
    public Counter counter() {
        return findOne(Counter.class);
    }

    /**
     * @return The first matching {@link Gauge}, or {@code null} if none match.
     */
    @Nullable
    public Gauge gauge() {
        return findOne(Gauge.class);
    }

    /**
     * @return The first matching {@link FunctionCounter}, or {@code null} if none match.
     */
    @Nullable
    public FunctionCounter functionCounter() {
        return findOne(FunctionCounter.class);
    }

    /**
     * @return The first matching {@link TimeGauge}, or {@code null} if none match.
     */
    @Nullable
    public TimeGauge timeGauge() {
        return findOne(TimeGauge.class);
    }

    /**
     * @return The first matching {@link FunctionTimer}, or {@code null} if none match.
     */
    @Nullable
    public FunctionTimer functionTimer() {
        return findOne(FunctionTimer.class);
    }

    /**
     * @return The first matching {@link DistributionSummary}, or {@code null} if none match.
     */
    @Nullable
    public DistributionSummary summary() {
        return findOne(DistributionSummary.class);
    }

    /**
     * @return The first matching {@link LongTaskTimer}, or {@code null} if none match.
     */
    @Nullable
    public LongTaskTimer longTaskTimer() {
        return findOne(LongTaskTimer.class);
    }

    /**
     * @return The first matching {@link Meter}, or {@code null} if none match.
     */
    @Nullable
    public Meter meter() {
        return findOne(Meter.class);
    }

    @Nullable
    private <M extends Meter> M findOne(Class<M> clazz) {
        return meterStream()
                .filter(clazz::isInstance)
                .findAny()
                .map(clazz::cast)
                .orElse(null);
    }

    /**
     * @return All matching meters, or an empty collection if none match.
     */
    public Collection<Meter> meters() {
        return meterStream().collect(toList());
    }

    private Stream<Meter> meterStream() {
        Stream<Meter> meterStream = registry.getMeters().stream().filter(m -> nameMatches.test(m.getId().getName()));

        if (!tags.isEmpty() || !requiredTagKeys.isEmpty()) {
            meterStream = meterStream.filter(m -> {
                boolean requiredKeysPresent = true;
                if (!requiredTagKeys.isEmpty()) {
                    final List<String> tagKeys = new ArrayList<>();
                    m.getId().getTags().forEach(t -> tagKeys.add(t.getKey()));
                    requiredKeysPresent = tagKeys.containsAll(requiredTagKeys);
                }

                return requiredKeysPresent && m.getId().getTags().containsAll(tags);
            });
        }

        return meterStream;
    }

    /**
     * @return All matching {@link Counter} meters.
     */
    public Collection<Counter> counters() {
        return findAll(Counter.class);
    }

    /**
     * @return All matching {@link Gauge} meters.
     */
    public Collection<Gauge> gauges() {
        return findAll(Gauge.class);
    }

    /**
     * @return All matching {@link Timer} meters.
     */
    public Collection<Timer> timers() {
        return findAll(Timer.class);
    }

    /**
     * @return All matching {@link DistributionSummary} meters.
     */
    public Collection<DistributionSummary> summaries() {
        return findAll(DistributionSummary.class);
    }

    /**
     * @return All matching {@link LongTaskTimer} meters.
     */
    public Collection<LongTaskTimer> longTaskTimers() {
        return findAll(LongTaskTimer.class);
    }

    /**
     * @return All matching {@link FunctionCounter} meters.
     */
    public Collection<FunctionCounter> functionCounters() {
        return findAll(FunctionCounter.class);
    }

    /**
     * @return All matching {@link FunctionTimer} meters.
     */
    public Collection<FunctionTimer> functionTimers() {
        return findAll(FunctionTimer.class);
    }

    /**
     * @return All matching {@link TimeGauge} meters.
     */
    public Collection<TimeGauge> timeGauges() {
        return findAll(TimeGauge.class);
    }

    private <M extends Meter> Collection<M> findAll(Class<M> clazz) {
        return meterStream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(toList());
    }

    /**
     * Initiate a new search for meters inside a registry.
     *
     * @param registry The registry to locate meters in.
     * @return A new search.
     */
    public static Search in(MeterRegistry registry) {
        return new Search(registry);
    }
}
