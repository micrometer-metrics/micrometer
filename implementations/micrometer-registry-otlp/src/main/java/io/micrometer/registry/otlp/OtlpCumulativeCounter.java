/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class OtlpCumulativeCounter extends CumulativeCounter implements StartTimeAwareMeter, OtlpExemplarsSupport {

    private final long startTimeNanos;

    private final @Nullable ExemplarSampler exemplarSampler;

    OtlpCumulativeCounter(Id id, Clock clock, @Nullable OtlpExemplarSamplerFactory exemplarSamplerFactory) {
        super(id);
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        this.exemplarSampler = exemplarSamplerFactory != null ? exemplarSamplerFactory.createExemplarSampler(16) : null;
    }

    @Override
    public void increment(double amount) {
        super.increment(amount);
        if (exemplarSampler != null) {
            exemplarSampler.sampleMeasurement(amount);
        }
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

    @Override
    public List<Exemplar> exemplars() {
        return exemplarSampler != null ? exemplarSampler.collectExemplars() : Collections.emptyList();
    }

}
