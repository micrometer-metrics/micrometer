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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.DoubleSupplier;

class StepMeasurement extends Measurement {

    private final StepDouble value;

    private final DoubleAdder lastCount = new DoubleAdder();

    StepMeasurement(DoubleSupplier f, Statistic statistic, Clock clock, long stepMillis) {
        super(f, statistic);
        this.value = new StepDouble(clock, stepMillis);
    }

    @Override
    public double getValue() {
        double absoluteCount = super.getValue();
        double inc = Math.max(0, absoluteCount - lastCount.sum());
        lastCount.add(inc);
        value.getCurrent().add(inc);

        return value.poll();
    }

}
