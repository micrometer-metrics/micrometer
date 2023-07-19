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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopCounter;

class CompositeCounter extends AbstractCompositeMeter<Counter> implements Counter {

    CompositeCounter(Meter.Id id) {
        super(id);
    }

    @Override
    public void increment(double amount) {
        for (Counter c : getChildren()) {
            c.increment(amount);
        }
    }

    @Override
    public double count() {
        return firstChild().count();
    }

    @Override
    Counter newNoopMeter() {
        return new NoopCounter(getId());
    }

    @Override
    Counter registerNewMeter(MeterRegistry registry) {
        return Counter.builder(getId().getName())
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .register(registry);
    }

}
