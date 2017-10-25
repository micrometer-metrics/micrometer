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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopGauge;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

public class CompositeGauge<T> extends AbstractMeter implements Gauge, CompositeMeter {
    protected final WeakReference<T> ref;
    protected final ToDoubleFunction<T> f;

    protected final Map<MeterRegistry, Gauge> gauges = new ConcurrentHashMap<>();

    CompositeGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.f = f;
    }

    @Override
    public double value() {
        return gauges.values().stream().findFirst().orElse(NoopGauge.INSTANCE).value();
    }

    @Override
    public void add(MeterRegistry registry) {
        T obj = ref.get();
        if(obj != null) {
            gauges.put(registry, Gauge.builder(getId().getName(), obj, f)
                .tags(getId().getTags())
                .description(getId().getDescription())
                .baseUnit(getId().getBaseUnit())
                .register(registry));
        }
    }

    @Override
    public void remove(MeterRegistry registry) {
        gauges.remove(registry);
    }
}
