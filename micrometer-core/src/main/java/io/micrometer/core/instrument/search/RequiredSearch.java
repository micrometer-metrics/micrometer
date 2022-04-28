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
package io.micrometer.core.instrument.search;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Search that requires the search terms are satisfiable, or an {@link MeterNotFoundException} is thrown.
 *
 * @author Jon Schneider
 */
public final class RequiredSearch {
    final MeterRegistry registry;

    final List<Tag> requiredTags = new ArrayList<>();
    final Set<String> requiredTagKeys = new HashSet<>();

    @Nullable
    String exactNameMatch;

    @Nullable
    Predicate<String> nameMatches;


    private RequiredSearch(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Meter contains a tag with the exact name.
     *
     * @param exactName Name to match against.
     * @return This search.
     */
    public RequiredSearch name(String exactName) {
        this.nameMatches = n -> n.equals(exactName);
        this.exactNameMatch = exactName;
        return this;
    }

    /**
     * Meter contains a tag matching the name predicate.
     *
     * @param nameMatches Name matching predicate.
     * @return This search.
     */
    public RequiredSearch name(Predicate<String> nameMatches) {
        this.nameMatches = nameMatches;
        return this;
    }

    /**
     * Meter contains a tag with the matching tag keys and values.
     *
     * @param tags The tags to match.
     * @return This search.
     */
    public RequiredSearch tags(Iterable<Tag> tags) {
        tags.forEach(this.requiredTags::add);
        return this;
    }

    /**
     * Meter contains a tag with the matching tag keys and values.
     *
     * @param tags Must be an even number of arguments representing key/value pairs of tags.
     * @return This search.
     */
    public RequiredSearch tags(String... tags) {
        return tags(Tags.of(tags));
    }

    /**
     * Meter contains a tag with the matching key and value.
     *
     * @param tagKey   The tag key to match.
     * @param tagValue The tag value to match.
     * @return This search.
     */
    public RequiredSearch tag(String tagKey, String tagValue) {
        return tags(Tags.of(tagKey, tagValue));
    }

    /**
     * Meter contains a tag with the matching keys.
     *
     * @param tagKeys The tag keys to match.
     * @return This search.
     */
    public RequiredSearch tagKeys(String... tagKeys) {
        Collections.addAll(requiredTagKeys, tagKeys);
        return this;
    }

    /**
     * @return The first matching {@link Timer}
     * @throws MeterNotFoundException if there is no match.
     */
    public Timer timer() {
        return findOne(Timer.class);
    }

    /**
     * @return The first matching {@link Counter}.
     * @throws MeterNotFoundException if there is no match.
     */
    public Counter counter() {
        return findOne(Counter.class);
    }

    /**
     * @return The first matching {@link Gauge}.
     * @throws MeterNotFoundException if there is no match.
     */
    public Gauge gauge() {
        return findOne(Gauge.class);
    }

    /**
     * @return The first matching {@link FunctionCounter}.
     * @throws MeterNotFoundException if there is no match.
     */
    public FunctionCounter functionCounter() {
        return findOne(FunctionCounter.class);
    }

    /**
     * @return The first matching {@link TimeGauge}.
     * @throws MeterNotFoundException if there is no match.
     */
    public TimeGauge timeGauge() {
        return findOne(TimeGauge.class);
    }

    /**
     * @return The first matching {@link FunctionTimer}.
     * @throws MeterNotFoundException if there is no match.
     */
    public FunctionTimer functionTimer() {
        return findOne(FunctionTimer.class);
    }

    /**
     * @return The first matching {@link DistributionSummary}.
     * @throws MeterNotFoundException if there is no match.
     */
    public DistributionSummary summary() {
        return findOne(DistributionSummary.class);
    }

    /**
     * @return The first matching {@link LongTaskTimer}.
     * @throws MeterNotFoundException if there is no match.
     */
    public LongTaskTimer longTaskTimer() {
        return findOne(LongTaskTimer.class);
    }

    /**
     * @return The first matching {@link Meter}.
     * @throws MeterNotFoundException if there is no match.
     */
    public Meter meter() {
        return findOne(Meter.class);
    }

    private <M extends Meter> M findOne(Class<M> clazz) {
        Optional<M> meter = meterStream()
                .filter(clazz::isInstance)
                .findAny()
                .map(clazz::cast);

        if (meter.isPresent()) {
            return meter.get();
        }

        throw MeterNotFoundException.forSearch(this, clazz);
    }

    private <M extends Meter> Collection<M> findAll(Class<M> clazz) {
        List<M> meters = meterStream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .collect(toList());

        if (meters.isEmpty()) {
            throw MeterNotFoundException.forSearch(this, clazz);
        }

        return meters;
    }

    /**
     * @return All matching meters.
     * @throws MeterNotFoundException if there is no match.
     */
    public Collection<Meter> meters() {
        List<Meter> meters = meterStream().collect(Collectors.toList());

        if (meters.isEmpty()) {
            throw MeterNotFoundException.forSearch(this, Meter.class);
        }

        return meters;
    }

    private Stream<Meter> meterStream() {
        Stream<Meter> meterStream = registry.getMeters().stream()
                .filter(m -> nameMatches == null || nameMatches.test(m.getId().getName()));

        if (!requiredTags.isEmpty() || !requiredTagKeys.isEmpty()) {
            meterStream = meterStream.filter(m -> {
                boolean requiredKeysPresent = true;
                if (!requiredTagKeys.isEmpty()) {
                    final List<String> tagKeys = new ArrayList<>();
                    m.getId().getTagsAsIterable().forEach(t -> tagKeys.add(t.getKey()));
                    requiredKeysPresent = tagKeys.containsAll(requiredTagKeys);
                }

                return requiredKeysPresent && m.getId().getTags().containsAll(requiredTags);
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

    /**
     * Initiate a new search for meters inside a registry.
     *
     * @param registry The registry to locate meters in.
     * @return A new search.
     */
    public static RequiredSearch in(MeterRegistry registry) {
        return new RequiredSearch(registry);
    }
}
