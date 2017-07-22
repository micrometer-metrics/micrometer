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
package io.micrometer.core.instrument.spectator.step;

import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.Gauge;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.impl.AtomicDouble;

import java.util.Collections;

class StepGauge implements Gauge {
    private final Id id;
    private final Clock clock;
    private final AtomicDouble value;

    /** Create a new instance. */
    public StepGauge(Id id, Clock clock) {
        this.id = id;
        this.clock = clock;
        this.value = new AtomicDouble(0.0);
    }

    @Override public Id id() {
        return id;
    }

    @Override public boolean hasExpired() {
        return false;
    }

    @Override public Iterable<Measurement> measure() {
        final Measurement m = new Measurement(id, clock.wallTime(), value());
        return Collections.singletonList(m);
    }

    @Override public void set(double v) {
        value.set(v);
    }

    @Override public double value() {
        return value.get();
    }
}
