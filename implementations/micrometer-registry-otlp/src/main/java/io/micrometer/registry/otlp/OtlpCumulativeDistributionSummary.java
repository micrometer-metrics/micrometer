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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.cumulative.CumulativeDistributionSummary;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;
import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class OtlpCumulativeDistributionSummary extends CumulativeDistributionSummary
        implements StartTimeAwareMeter, OtlpHistogramSupport, OtlpExemplarMeter {

    private final HistogramFlavor histogramFlavor;

    private final long startTimeNanos;

    @Nullable
    private final ExemplarCollector exemplarCollector;

    OtlpCumulativeDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, OtlpConfig otlpConfig, @Nullable ExemplarCollectorFactory exemplarCollectorFactory) {
        super(id, clock, distributionStatisticConfig, scale,
                OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig, otlpConfig));
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        this.histogramFlavor = OtlpMeterRegistry.histogramFlavor(otlpConfig.histogramFlavor(),
                distributionStatisticConfig);

        this.exemplarCollector = exemplarCollectorFactory == null
            ? null
            : exemplarCollectorFactory.forHistogram(distributionStatisticConfig, otlpConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        super.recordNonNegative(amount);
        if (exemplarCollector != null) {
            exemplarCollector.offerMeasurement(amount);
        }
    }

    @Override
    public long getStartTimeNanos() {
        return this.startTimeNanos;
    }

    @Override
    @Nullable
    public ExponentialHistogramSnapShot getExponentialHistogramSnapShot() {
        if (histogramFlavor == HistogramFlavor.BASE2_EXPONENTIAL_BUCKET_HISTOGRAM) {
            return ((Base2ExponentialHistogram) histogram).getLatestExponentialHistogramSnapshot();
        }
        return null;
    }

    @Override
    public List<Exemplar> exemplars() {
        return exemplarCollector == null ? Collections.emptyList() : exemplarCollector.collectAndReset();
    }
}
