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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.util.MeterId;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

class CompositeCounter implements Counter, CompositeMeter {
    private final MeterId id;
    private final Map<MeterRegistry, Counter> counters = Collections.synchronizedMap(new LinkedHashMap<>());

    CompositeCounter(MeterId id) {
        this.id = id;
    }

    @Override
    public void increment(double amount) {
        synchronized (counters) {
            counters.values().forEach(Counter::increment);
        }
    }

    @Override
    public double count() {
        synchronized (counters) {
            return counters.values().stream().findFirst().orElse(NoopCounter.INSTANCE).count();
        }
    }

    @Override
    public void add(MeterRegistry registry) {
        synchronized (counters) {
            counters.put(registry, registry.counter(id.getName(), id.getTags()));
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (counters) {
            counters.remove(registry);
        }
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Iterable<Tag> getTags() {
        return id.getTags();
    }

    @Override
    public List<Measurement> measure() {
        synchronized (counters) {
            return counters.values().stream().flatMap(c -> c.measure().stream()).collect(toList());
        }
    }
}
