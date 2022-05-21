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
package io.micrometer.core.instrument.dropwizard;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;

/**
 * @author Jon Schneider
 */
public class DropwizardGauge extends AbstractMeter implements Gauge {

    private final com.codahale.metrics.Gauge<Double> impl;

    DropwizardGauge(Meter.Id id, com.codahale.metrics.Gauge<Double> impl) {
        super(id);
        this.impl = impl;
    }

    @Override
    public double value() {
        Double value = impl.getValue();
        return value == null ? Double.NaN : value;
    }

}
