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

import org.springframework.metrics.instrument.Gauge;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.internal.MeterId;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.function.ToDoubleFunction;

public class SimpleGauge<T> implements Gauge {
    private final MeterId id;
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;

    public SimpleGauge(MeterId id, T obj, ToDoubleFunction<T> value) {
        this.id = id;
        this.ref = new WeakReference<>(obj);
        this.value = value;
    }

    @Override
    public double value() {
        return value.applyAsDouble(ref.get());
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public Tag[] getTags() {
        return id.getTags();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Collections.singletonList(id.withTags(Tag.of("type", "GAUGE")).measurement(value()));
    }
}
