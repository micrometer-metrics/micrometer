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
import io.micrometer.core.instrument.step.StepTuple2;

import java.util.function.Supplier;

/**
 * This is an internal class not meant for general use. The only reason to have this class
 * is that {@code OtlpMeterRegistry} can call {@code _closingRollover} on
 * {@code StepTuple2} and the method does not need to be public.
 */
class OtlpStepTuple2<T1, T2> extends StepTuple2<T1, T2> {

    OtlpStepTuple2(Clock clock, long stepMillis, T1 t1NoValue, T2 t2NoValue, Supplier<T1> t1Supplier,
            Supplier<T2> t2Supplier) {
        super(clock, stepMillis, t1NoValue, t2NoValue, t1Supplier, t2Supplier);
    }

    @Override
    protected void _closingRollover() {
        super._closingRollover();
    }

}
