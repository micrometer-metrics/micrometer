/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.wavefront;

import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Timer that sends histogram distributions to Wavefront using WavefrontHistogramImpl.
 *
 * @author Jon Schneider
 */
class WavefrontLongTaskTimer extends DefaultLongTaskTimer {

    @Nullable
    private final WavefrontHistogramImpl histogram;

    WavefrontLongTaskTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            TimeUnit baseTimeUnit) {
        super(id, clock, baseTimeUnit, distributionStatisticConfig, false);
        histogram = distributionStatisticConfig.isPublishingHistogram() ? new WavefrontHistogramImpl(clock::wallTime)
                : null;
    }

    List<WavefrontHistogramImpl.Distribution> flushDistributions() {
        if (histogram == null) {
            return Collections.emptyList();
        }
        else {
            forEachActive(s -> histogram.update(s.duration(baseTimeUnit())));
            return histogram.flushDistributions();
        }
    }

    boolean isPublishingHistogram() {
        return histogram != null;
    }

}
