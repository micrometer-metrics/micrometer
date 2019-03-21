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

import io.micrometer.core.instrument.FunctionTimer;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class AggregateFunctionTimer extends AggregateMeter implements FunctionTimer {
    private Collection<FunctionTimer> timers;

    AggregateFunctionTimer(Id aggregateId, Collection<FunctionTimer> timers) {
        super(aggregateId);
        this.timers = timers;
    }

    @Override
    public double count() {
        return timers.stream().mapToDouble(FunctionTimer::count).sum();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return timers.stream().mapToDouble(t -> t.totalTime(unit)).sum();
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return timers.iterator().next().baseTimeUnit();
    }
}