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
import io.micrometer.core.instrument.distribution.*;
import io.micrometer.registry.otlp.internal.Base2ExponentialHistogram;
import io.micrometer.registry.otlp.internal.ExponentialHistogramSnapShot;

import java.util.concurrent.TimeUnit;

class OtlpCumulativeDistributionSummary extends CumulativeDistributionSummary
        implements StartTimeAwareMeter, OtlpHistogramSupport {

    private final HistogramFlavor histogramFlavor;

    private final long startTimeNanos;

    OtlpCumulativeDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            double scale, OtlpConfig otlpConfig) {
        super(id, clock, distributionStatisticConfig, scale,
                OtlpMeterRegistry.getHistogram(clock, distributionStatisticConfig, otlpConfig));
        this.startTimeNanos = TimeUnit.MILLISECONDS.toNanos(clock.wallTime());
        this.histogramFlavor = OtlpMeterRegistry.histogramFlavor(otlpConfig.histogramFlavor(),
                distributionStatisticConfig);
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

}
