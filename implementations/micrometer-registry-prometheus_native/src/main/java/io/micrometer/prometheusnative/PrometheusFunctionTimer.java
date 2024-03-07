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
package io.micrometer.prometheusnative;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.prometheus.metrics.core.metrics.SummaryWithCallback;
import io.prometheus.metrics.model.snapshots.SummarySnapshot.SummaryDataPointSnapshot;

import java.util.concurrent.TimeUnit;

public class PrometheusFunctionTimer extends PrometheusMeter<SummaryWithCallback, SummaryDataPointSnapshot>
        implements FunctionTimer {

    public PrometheusFunctionTimer(Meter.Id id, SummaryWithCallback summary) {
        super(id, summary);
    }

    @Override
    public double count() {
        return collect().getCount();
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return toUnit(collect().getSum(), unit);
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return TimeUnit.SECONDS;
    }

}
