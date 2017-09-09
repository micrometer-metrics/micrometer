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

import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

public class CompositeTimeGauge<T> extends CompositeGauge<T> {
    private final TimeUnit fUnit;

    CompositeTimeGauge(Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        super(id, obj, f);
        this.fUnit = fUnit;
    }

    @Override
    public void add(MeterRegistry registry) {
        T obj = ref.get();
        if(obj != null) {
            synchronized (gauges) {
                gauges.put(registry, registry.more().timeGauge(getId(), obj, fUnit, f));
            }
        }
    }
}
