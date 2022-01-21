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
package io.micrometer.api.instrument.dropwizard;

import com.codahale.metrics.Meter;
import io.micrometer.api.instrument.AbstractMeter;
import io.micrometer.api.instrument.Counter;
import io.micrometer.api.instrument.util.MeterEquivalence;

/**
 * @author Jon Schneider
 */
public class DropwizardCounter extends AbstractMeter implements Counter {
    private final com.codahale.metrics.Meter impl;

    DropwizardCounter(io.micrometer.api.instrument.Meter.Id id, Meter impl) {
        super(id);
        this.impl = impl;
    }

    @Override
    public void increment(double amount) {
        impl.mark((long) amount);
    }

    @Override
    public double count() {
        return impl.getCount();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }
}
