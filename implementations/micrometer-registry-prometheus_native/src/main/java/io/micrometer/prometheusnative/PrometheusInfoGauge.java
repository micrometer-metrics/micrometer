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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;

/**
 * Prometheus Info metric.
 * <p>
 * Micrometer implements Info metrics as Gauge.
 */
public class PrometheusInfoGauge implements Gauge {

    private final Meter.Id id;

    public PrometheusInfoGauge(Id id) {
        this.id = id;
    }

    @Override
    public double value() {
        return 1.0;
    }

    @Override
    public Id getId() {
        return id;
    }

}
