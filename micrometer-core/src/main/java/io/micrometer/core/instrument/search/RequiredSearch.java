/**
 * Copyright 2017 Pivotal Software, Inc.
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Search that requires the search terms are satisfiable, or an {@link MeterNotFoundException} is thrown.
 *
 * @author Jon Schneider
 */
public final class RequiredSearch {
    private final MeterRegistry registry;
    private final List<Tag> tags = new ArrayList<>();
    private Predicate<String> nameMatches = n -> true;
    private final Set<String> requiredTagKeys = new HashSet<>();

    @Nullable
    private String exactNameMatch;

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
        tags.forEach(this.tags::add);
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
        requiredTagKeys.addAll(Arrays.asList(tagKeys));
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
        Optional<M> meter = meters()
            .stream()
            .filter(clazz::isInstance)
            .findAny()
            .map(clazz::cast);

        if (meter.isPresent()) {
            return meter.get();
        }

        throw new MeterNotFoundException(exactNameMatch, tags, clazz);
    }

    /**
     * @return All matching meters.
     * @throws MeterNotFoundException if there is no match.
     */
    public Collection<Meter> meters() {
        Stream<Meter> meterStream = registry.getMeters().stream().filter(m -> nameMatches.test(m.getId().getName()));

        if (!tags.isEmpty() || !requiredTagKeys.isEmpty()) {
            meterStream = meterStream.filter(m -> {
                boolean requiredKeysPresent = true;
                if (!requiredTagKeys.isEmpty()) {
                    final List<String> tagKeys = new ArrayList<>();
                    m.getId().getTags().forEach(t -> tagKeys.add(t.getKey()));
                    requiredKeysPresent = tagKeys.containsAll(requiredTagKeys);
                }

                return m.getId().getTags().containsAll(tags) && requiredKeysPresent;
            });
        }

        List<Meter> meters = meterStream.collect(Collectors.toList());

        if(meters.isEmpty()) {
            throw new MeterNotFoundException(exactNameMatch, tags, Meter.class);
        }

        return meters;
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
