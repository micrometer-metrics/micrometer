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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopCounter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class CompositeCounter extends AbstractMeter implements Counter, CompositeMeter {
    private final Map<MeterRegistry, Counter> counters = new ConcurrentHashMap<>();

    CompositeCounter(Meter.Id id) {
        super(id);
    }

    @Override
    public void increment(double amount) {
        counters.values().forEach(c -> c.increment(amount));
    }

    @Override
    public double count() {
        return counters.values().stream().findFirst().orElse(new NoopCounter(getId())).count();
    }

    @Override
    public void add(MeterRegistry registry) {
        counters.put(registry, Counter.builder(getId().getName())
            .tags(getId().getTags())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .register(registry));
    }

    @Override
    public void remove(MeterRegistry registry) {
        counters.remove(registry);
    }
}
