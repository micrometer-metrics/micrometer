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

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

abstract class AbstractCompositeMeter<T extends Meter> extends AbstractMeter implements CompositeMeter {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<AbstractCompositeMeter, Meter> firstMeterUpdater =
            AtomicReferenceFieldUpdater.newUpdater(AbstractCompositeMeter.class, Meter.class, "firstMeter");

    private final List<Entry> children = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unused")
    private volatile T firstMeter;
    private volatile T noopMeter;

    AbstractCompositeMeter(Id id) {
        super(id);
    }

    abstract T newNoopMeter();
    abstract T registerNewMeter(MeterRegistry registry);

    final void forEachChild(Consumer<T> task) {
        children.forEach(e -> task.accept(e.meter()));
    }

    final Stream<T> childStream() {
        return children.stream().map(Entry::meter);
    }

    final T firstChild() {
        for (;;) {
            T firstMeter = this.firstMeter;
            if (firstMeter != null) {
                return firstMeter;
            }

            firstMeter = findFirstChild();
            if (firstMeter == null) {
                break;
            }

            if (firstMeterUpdater.compareAndSet(this, null, firstMeter)) {
                return firstMeter;
            }
        }

        // There are no child meters at the moment. Return a lazily instantiated no-op meter.
        final T noopMeter = this.noopMeter;
        if (noopMeter != null) {
            return noopMeter;
        } else {
            return this.noopMeter = newNoopMeter();
        }
    }

    private T findFirstChild() {
        if (children.isEmpty()) {
            return null;
        }

        final Iterator<Entry> i = children.iterator();
        return i.hasNext() ? i.next().meter() : null;
    }

    @Override
    public final void add(MeterRegistry registry) {
        final T newMeter = registerNewMeter(registry);
        if (newMeter == null) {
            return;
        }

        children.add(new Entry(registry, newMeter));
        firstMeterUpdater.compareAndSet(this, null, newMeter);
    }

    @Override
    public final void remove(MeterRegistry registry) {
        // Not very efficient, but this operation is expected to be used rarely.
        final AtomicReference<T> meterHolder = new AtomicReference<>();
        children.removeIf(e -> {
            if (e.registry() == registry) {
                meterHolder.set(e.meter());
                return true;
            } else {
                return false;
            }
        });

        final T removedMeter = meterHolder.get();
        if (removedMeter != null) {
            firstMeterUpdater.compareAndSet(this, removedMeter, null);
        }
    }

    private static final class Entry {
        private final MeterRegistry registry;
        private final Meter meter;

        Entry(MeterRegistry registry, Meter meter) {
            this.registry = registry;
            this.meter = meter;
        }

        MeterRegistry registry() {
            return registry;
        }

        @SuppressWarnings("unchecked")
        <U> U meter() {
            return (U) meter;
        }
    }
}
