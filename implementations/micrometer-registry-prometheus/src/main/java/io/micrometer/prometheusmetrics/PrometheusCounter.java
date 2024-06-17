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
package io.micrometer.prometheusmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.prometheus.metrics.core.exemplars.ExemplarSampler;
import io.prometheus.metrics.model.snapshots.Exemplar;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * {@link Counter} for Prometheus.
 *
 * @author Jon Schneider
 * @author Jonatan Ivanov
 * @since 1.13.0
 */
public class PrometheusCounter extends AbstractMeter implements Counter {

    private final DoubleAdder count = new DoubleAdder();

    @Nullable
    private final ExemplarSampler exemplarSampler;

    PrometheusCounter(Meter.Id id) {
        this(id, null);
    }

    PrometheusCounter(Meter.Id id, @Nullable ExemplarSamplerFactory exemplarSamplerFactory) {
        super(id);
        this.exemplarSampler = exemplarSamplerFactory != null ? exemplarSamplerFactory.createExemplarSampler(1) : null;
    }

    @Override
    public void increment(double amount) {
        if (amount > 0) {
            count.add(amount);
            if (exemplarSampler != null) {
                exemplarSampler.observe(amount);
            }
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    @Nullable
    Exemplar exemplar() {
        return exemplarSampler != null ? exemplarSampler.collect().getLatest() : null;
    }

}
