/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.simple;

import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;
import org.springframework.metrics.instrument.internal.MeterId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends AbstractMeterRegistry {
    private final Map<MeterId, Meter> meterMap = new HashMap<>();

    public SimpleMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public SimpleMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        return (Counter) meterMap.computeIfAbsent(new MeterId(name, tags), id -> new SimpleCounter(name));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags) {
        return (DistributionSummary) meterMap.computeIfAbsent(new MeterId(name, tags), id -> new SimpleDistributionSummary(name));
    }

    @Override
    public Timer timer(String name, Iterable<Tag> tags) {
        return (Timer) meterMap.computeIfAbsent(new MeterId(name, tags), id -> new SimpleTimer(name));
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        return (LongTaskTimer) meterMap.computeIfAbsent(new MeterId(name, tags), id -> new SimpleLongTaskTimer(name, getClock()));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        meterMap.computeIfAbsent(new MeterId(name, tags), id -> new SimpleGauge<>(name, obj, f));
        return obj;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }
}
