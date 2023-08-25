/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepValue;

import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.Supplier;

/**
 * A {@link StepValue} implementation that tracks max over a step interval.
 *
 * @author Lenin Jaganathan
 */
class StepMax extends StepValue<Double> {

    private final DoubleAccumulator current = new DoubleAccumulator(Double::max, 0d);

    StepMax(Clock clock, long stepMillis) {
        super(clock, stepMillis);
    }

    @Override
    protected Supplier<Double> valueSupplier() {
        return current::getThenReset;
    }

    @Override
    protected Double noValue() {
        return 0.0;
    }

    void record(double value) {
        current.accumulate(value);
    }

    @Override
    protected void _closingRollover() {
        super._closingRollover();
    }

}
