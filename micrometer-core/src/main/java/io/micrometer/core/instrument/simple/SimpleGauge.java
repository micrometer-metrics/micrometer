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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.util.Meters;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.util.MeterId;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class SimpleGauge<T> extends AbstractSimpleMeter implements Gauge {
    private static final Tag TYPE_TAG = SimpleUtils.typeTag(Type.Gauge);
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;
    private final MeterId gaugeId;

    public SimpleGauge(MeterId id, T obj, ToDoubleFunction<T> value) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.value = value;
        this.gaugeId = id.withTags(TYPE_TAG);
    }

    @Override
    public double value() {
        return value.applyAsDouble(ref.get());
    }

    @Override
    public List<Measurement> measure() {
        return Collections.singletonList(gaugeId.measurement(value()));
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return Meters.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Meters.hashCode(this);
    }
}
