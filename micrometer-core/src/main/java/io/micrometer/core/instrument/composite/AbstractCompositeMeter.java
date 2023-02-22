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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractCompositeMeter<T extends Meter> extends AbstractMeter implements CompositeMeter {

    private final AtomicBoolean childrenGuard = new AtomicBoolean();

    private Map<MeterRegistry, T> children = Collections.emptyMap();

    @Nullable
    private volatile T noopMeter;

    AbstractCompositeMeter(Id id) {
        super(id);
    }

    abstract T newNoopMeter();

    @Nullable
    abstract T registerNewMeter(MeterRegistry registry);

    final Iterable<T> getChildren() {
        return children.values();
    }

    T firstChild() {
        final Iterator<T> i = children.values().iterator();
        if (i.hasNext())
            return i.next();

        // There are no child meters. Return a lazily instantiated no-op meter.
        final T noopMeter = this.noopMeter;
        if (noopMeter != null) {
            return noopMeter;
        }
        else {
            // noinspection ConstantConditions
            return this.noopMeter = newNoopMeter();
        }
    }

    public final void add(MeterRegistry registry) {
        final T newMeter = registerNewMeter(registry);
        if (newMeter == null) {
            return;
        }

        for (;;) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    Map<MeterRegistry, T> newChildren = new IdentityHashMap<>(children);
                    newChildren.put(registry, newMeter);
                    this.children = newChildren;
                    break;
                }
                finally {
                    childrenGuard.set(false);
                }
            }
        }
    }

    /**
     * Does nothing. New registries added to the composite are automatically reflected in
     * each meter belonging to the composite.
     * @param registry The registry to remove.
     */
    @Deprecated
    public final void remove(MeterRegistry registry) {
        for (;;) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    Map<MeterRegistry, T> newChildren = new IdentityHashMap<>(children);
                    newChildren.remove(registry);
                    this.children = newChildren;
                    break;
                }
                finally {
                    childrenGuard.set(false);
                }
            }
        }
    }

}
