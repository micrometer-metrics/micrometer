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

import io.micrometer.common.lang.Nullable;
import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.model.snapshots.CounterSnapshot.CounterDataPointSnapshot;
import io.prometheus.metrics.model.snapshots.Exemplar;

public class PrometheusCounter extends PrometheusMeter<Counter, CounterDataPointSnapshot>
        implements io.micrometer.core.instrument.Counter {

    private final CounterDataPoint dataPoint;

    PrometheusCounter(Id id, Counter counter, CounterDataPoint dataPoint) {
        super(id, counter);
        this.dataPoint = dataPoint;
    }

    @Override
    public void increment(double amount) {
        dataPoint.inc(amount);
    }

    @Override
    public double count() {
        return collect().getValue();
    }

    @Nullable
    Exemplar exemplar() {
        return collect().getExemplar();
    }

}
