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

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopCounter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class CompositeCounter extends AbstractMeter implements Counter, CompositeMeter {
    private final Map<MeterRegistry, Counter> counters = Collections.synchronizedMap(new LinkedHashMap<>());

    CompositeCounter(Meter.Id id) {
        super(id);
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
            counters.put(registry, registry.counter(getId()));
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        synchronized (counters) {
            counters.remove(registry);
        }
    }
}
