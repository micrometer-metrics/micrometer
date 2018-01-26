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
import io.micrometer.core.lang.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Search {
    private final MeterRegistry registry;
    private final String name;
    private final List<Tag> tags = new ArrayList<>();

    public Search(MeterRegistry registry, String name) {
        this.registry = registry;
        this.name = name;
    }

    public Search tags(Iterable<Tag> tags) {
        tags.forEach(this.tags::add);
        return this;
    }

    /**
     * @param tags Must be an even number of arguments representing key/value pairs of tags.
     */
    public Search tags(String... tags) {
        return tags(Tags.of(tags));
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
        Stream<Meter> meterStream =
            registry.getMeters().stream().filter(m -> m.getId().getName().equals(name));

        if (!tags.isEmpty()) {
            meterStream = meterStream.filter(m -> {
                final List<Tag> idTags = new ArrayList<>();
                m.getId().getTags().forEach(idTags::add);
                return idTags.containsAll(tags);
            });
        }

        return meterStream.collect(Collectors.toList());
    }
}
