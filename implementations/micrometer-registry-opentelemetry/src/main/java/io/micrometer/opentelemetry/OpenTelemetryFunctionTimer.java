/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.opentelemetry;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.api.metrics.DoubleValueObserver;

public class OpenTelemetryFunctionTimer<T> extends CumulativeFunctionTimer<T> {
    private final DoubleValueObserver countObserver;
    private final DoubleValueObserver durationObserver;
    private final Attributes attributes;

    public OpenTelemetryFunctionTimer(Id id, T obj,
                                      ToLongFunction<T> countFunction, DoubleValueObserver countObserver,
                                      ToDoubleFunction<T> totalTimeFunction, DoubleValueObserver valueObserver,
                                      TimeUnit totalTimeFunctionUnit, TimeUnit baseTimeUnit,
                                      Attributes attributes) {
        super(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, baseTimeUnit);
        this.countObserver = countObserver;
        this.countObserver.setCallback(this::updateCount);

        this.durationObserver = valueObserver;
        this.durationObserver.setCallback(this::updateDuration);

        this.attributes = attributes;
    }

    private void updateDuration(AsynchronousInstrument.DoubleResult doubleResult) {
        doubleResult.observe(totalTime(baseTimeUnit()), attributes);
    }

    private void updateCount(AsynchronousInstrument.DoubleResult doubleResult) {
        doubleResult.observe(count(), attributes);
    }
}
