/*
 * Copyright 2019 VMware, Inc.
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
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Timer that sends histogram distributions to Wavefront using WavefrontHistogramImpl.
 *
 * @author Han Zhang
 */
class WavefrontTimer extends CumulativeTimer {

    @Nullable
    private final WavefrontHistogramImpl delegate;

    WavefrontTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector, TimeUnit baseTimeUnit) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, false);
        delegate = distributionStatisticConfig.isPublishingHistogram() ? new WavefrontHistogramImpl(clock::wallTime)
                : null;
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        super.recordNonNegative(amount, unit);
        if (delegate != null) {
            delegate.update(TimeUtils.convert(amount, unit, baseTimeUnit()));
        }
    }

    List<WavefrontHistogramImpl.Distribution> flushDistributions() {
        if (delegate == null) {
            return Collections.emptyList();
        }
        else {
            return delegate.flushDistributions();
        }
    }

    boolean isPublishingHistogram() {
        return delegate != null;
    }

}
