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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.prometheus.internal.CustomPrometheusLongTaskTimer;

import java.util.concurrent.TimeUnit;

public class PrometheusLongTaskTimer extends AbstractMeter implements LongTaskTimer {
    private final CustomPrometheusLongTaskTimer.Child timer;

    PrometheusLongTaskTimer(Meter.Id id, CustomPrometheusLongTaskTimer.Child timer) {
        super(id);
        this.timer = timer;
    }

    @Override
    public long start() {
        return timer.start();
    }

    @Override
    public long stop(long task) {
        return timer.stop(task);
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        return timer.duration(task, unit);
    }

    @Override
    public double duration(TimeUnit unit) {
        return timer.duration(unit);
    }

    @Override
    public int activeTasks() {
        return timer.activeTasks();
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
