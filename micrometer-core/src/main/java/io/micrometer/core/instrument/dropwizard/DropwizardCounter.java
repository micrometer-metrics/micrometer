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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Meter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.util.Meters;
import io.micrometer.core.instrument.util.MeterId;

import java.util.Collections;
import java.util.List;

/**
 * @author Jon Schneider
 */
public class DropwizardCounter extends AbstractDropwizardMeter implements Counter {
    private final com.codahale.metrics.Meter impl;

    public DropwizardCounter(MeterId id, Meter impl) {
        super(id);
        this.impl = impl;
    }

    @Override
    public List<Measurement> measure() {
        return Collections.singletonList(id.measurement(count()));
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
        return Meters.equals(this, o);
    }

    @Override
    public int hashCode() {
        return Meters.hashCode(this);
    }
}
