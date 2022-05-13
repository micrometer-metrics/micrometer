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
package io.micrometer.statsd;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import reactor.core.publisher.FluxSink;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

public class StatsdGauge<T> extends AbstractMeter implements Gauge, StatsdPollable {

    private final StatsdLineBuilder lineBuilder;

    private final FluxSink<String> sink;

    private final WeakReference<T> ref;

    private final ToDoubleFunction<T> value;

    private final AtomicReference<Double> lastValue = new AtomicReference<>(Double.NaN);

    private final boolean alwaysPublish;

    StatsdGauge(Id id, StatsdLineBuilder lineBuilder, FluxSink<String> sink, @Nullable T obj, ToDoubleFunction<T> value,
            boolean alwaysPublish) {
        super(id);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
        this.ref = new WeakReference<>(obj);
        this.value = value;
        this.alwaysPublish = alwaysPublish;
    }

    @Override
    public double value() {
        T obj = ref.get();
        return obj != null ? value.applyAsDouble(obj) : Double.NaN;
    }

    @Override
    public void poll() {
        double val = value();
        if (Double.isFinite(val) && (alwaysPublish || lastValue.getAndSet(val) != val)) {
            sink.next(lineBuilder.gauge(val));
        }
    }

}
