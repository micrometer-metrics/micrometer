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
package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;
import io.micrometer.registry.otlp.internal.OtlpExemplarsSupport;
import org.jspecify.annotations.Nullable;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class OtlpCumulativeTimer extends CumulativeTimer
        implements StartTimeAwareMeter, OtlpHistogramSupport, OtlpExemplarsSupport {

    private final long startTimeNanos;

    private final @Nullable ExemplarSampler exemplarSampler;

    OtlpCumulativeTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit, Histogram histogram,
            @Nullable OtlpExemplarSamplerFactory exemplarSamplerFactory) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, histogram);
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        if (histogram instanceof OtlpExemplarsSupport) {
            this.exemplarSampler = null;
        }
        else {
            this.exemplarSampler = exemplarSamplerFactory != null ? exemplarSamplerFactory.create(1, true) : null;
        }
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

    @Override
    public @Nullable ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
        if (histogram instanceof Base2ExponentialHistogram) {
            return ((Base2ExponentialHistogram) histogram).getLatestExponentialHistogramSnapshot();
        }
        return null;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        super.recordNonNegative(amount, unit);
        if (exemplarSampler != null) {
            exemplarSampler.sampleMeasurement((double) unit.toNanos(amount));
        }
    }

    @Override
    public List<Exemplar> exemplars() {
        if (exemplarSampler != null) {
            return exemplarSampler.collectExemplars();
        }
        else if (histogram instanceof OtlpExemplarsSupport) {
            return ((OtlpExemplarsSupport) histogram).exemplars();
        }
        else {
            return Collections.emptyList();
        }
    }

    @Override
    public void closingExemplarsRollover() {
        if (exemplarSampler != null) {
            exemplarSampler.close();
        }
        else if (histogram instanceof OtlpExemplarsSupport) {
            ((OtlpExemplarsSupport) histogram).closingExemplarsRollover();
        }
    }

}
