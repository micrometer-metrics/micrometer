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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.internal.DefaultMeter;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

class CompositeCustomMeter extends DefaultMeter implements CompositeMeter {

    private final AtomicBoolean childrenGuard = new AtomicBoolean();

    private IdentityHashMap<MeterRegistry, Meter> children = new IdentityHashMap<>();

    CompositeCustomMeter(Id id, Type type, Iterable<Measurement> measurements) {
        super(id, type, measurements);
    }

    @Override
    public @Nullable Meter add(MeterRegistry registry) {
        Meter newMeter = Meter.builder(getId().getName(), getType(), measure())
            .tags(getId().getTagsAsIterable())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .register(registry);

        for (;;) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    IdentityHashMap<MeterRegistry, Meter> newChildren = new IdentityHashMap<>(children);
                    Meter previous = newChildren.put(registry, newMeter);
                    this.children = newChildren;
                    return previous == null ? newMeter : null;
                }
                finally {
                    childrenGuard.set(false);
                }
            }
        }
    }

    @Override
    public @Nullable Meter remove(MeterRegistry registry) {
        for (;;) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    IdentityHashMap<MeterRegistry, Meter> newChildren = new IdentityHashMap<>(children);
                    Meter removed = newChildren.remove(registry);
                    this.children = newChildren;
                    return removed;
                }
                finally {
                    childrenGuard.set(false);
                }
            }
        }
    }

}
