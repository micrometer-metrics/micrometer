/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.cloudwatch.aggregate;

import io.micrometer.core.instrument.LongTaskTimer;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class AggregateLongTaskTimer extends AggregateMeter implements LongTaskTimer {
    private Collection<LongTaskTimer> longTaskTimers;

    AggregateLongTaskTimer(Id aggregateId, Collection<LongTaskTimer> longTaskTimers) {
        super(aggregateId);
        this.longTaskTimers = longTaskTimers;
    }

    @Override
    public double duration(TimeUnit unit) {
        return longTaskTimers.stream().mapToDouble(ltt -> ltt.duration(unit)).sum();
    }

    @Override
    public int activeTasks() {
        return longTaskTimers.stream().mapToInt(LongTaskTimer::activeTasks).sum();
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        throw new UnsupportedOperationException("Reporting should not require the duration of an individual task");
    }

    @Override
    public Sample start() {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }

    @Override
    public long stop(long task) {
        throw new UnsupportedOperationException("This aggregate is only used for reporting, not recording");
    }
}