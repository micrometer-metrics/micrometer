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
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public Search tags(Iterable<Tag> tags) {
        tags.forEach(this.tags::add);
        return this;
    }

    public Search name(String exactName) {
        return name(n -> n.equals(exactName));
    }

    public Search name(Predicate<String> nameMatches) {
        this.nameMatches = nameMatches;
        return this;
    }

    /**
     * Meter contains a tag with the matching tag keys and values.
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
     * Meter contains a tag with the matching keys.
     *
     * @param tagKeys The tag keys to match.
     * @return This search.
     */
    public Search tagKeys(String... tagKeys) {
        requiredTagKeys.addAll(Arrays.asList(tagKeys));
        return this;
    }

    @Nullable
    public Timer timer() {
        return findOne(Timer.class);
    }

    @Nullable
    public Counter counter() {
        return findOne(Counter.class);
    }

    @Nullable
    public Gauge gauge() {
        return findOne(Gauge.class);
    }

    @Nullable
    public FunctionCounter functionCounter() {
        return findOne(FunctionCounter.class);
    }

    @Nullable
    public TimeGauge timeGauge() {
        return findOne(TimeGauge.class);
    }

    @Nullable
    public FunctionTimer functionTimer() {
        return findOne(FunctionTimer.class);
    }

    @Nullable
    public DistributionSummary summary() {
        return findOne(DistributionSummary.class);
    }

    @Nullable
    public LongTaskTimer longTaskTimer() {
        return findOne(LongTaskTimer.class);
    }

    @Nullable
    public Meter meter() {
        return findOne(Meter.class);
    }

    @Nullable
    private <T> T findOne(Class<T> clazz) {
        return meters()
                .stream()
                .filter(clazz::isInstance)
                .findAny()
                .map(clazz::cast)
                .orElse(null);
    }

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

        return meterStream.collect(Collectors.toList());
    }

    public static Search search(MeterRegistry registry) {
        return new Search(registry);
    }
}
