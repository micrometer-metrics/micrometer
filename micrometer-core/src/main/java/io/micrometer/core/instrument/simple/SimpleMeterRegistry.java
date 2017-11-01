/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.PercentileHistogramBuckets;
import io.micrometer.core.instrument.histogram.StatsConfig;
import io.micrometer.core.instrument.step.StepMeterRegistry;

import java.text.DecimalFormat;
import java.util.concurrent.TimeUnit;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends StepMeterRegistry {
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");

    public SimpleMeterRegistry() {
        this(SimpleConfig.DEFAULT, Clock.SYSTEM);
    }

    public SimpleMeterRegistry(SimpleConfig config, Clock clock) {
        super(config, clock);
    }

    @Override
    protected void publish() {
        // don't publish anywhere
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, StatsConfig statsConfig) {
        DistributionSummary summary = super.newDistributionSummary(id, statsConfig);

        for (double percentile : statsConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, summary::percentile);
        }

        if(statsConfig.isPublishingHistogram()) {
            for (Long bucket : statsConfig.getHistogramBuckets(false)) {
                more().counter(getConventionName(id), Tags.concat(getConventionTags(id), "bucket", Long.toString(bucket)),
                    summary, s -> s.histogramCountAtValue(bucket));
            }
        }

        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, StatsConfig statsConfig) {
        Timer timer = super.newTimer(id, statsConfig);

        for (double percentile : statsConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, p -> timer.percentile(p, getBaseTimeUnit()));
        }

        if(statsConfig.isPublishingHistogram()) {
            for (Long bucket : statsConfig.getHistogramBuckets(false)) {
                more().counter(getConventionName(id), Tags.concat(getConventionTags(id), "bucket", Long.toString(bucket)),
                    timer, t -> t.histogramCountAtValue(bucket));
            }
        }

        return timer;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
