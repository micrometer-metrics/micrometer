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
package io.micrometer.concurrencytest;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.DDD_Result;

@JCStressTest
@Outcome(id = "0.0, 0.0, 0.0", expect = Expect.ACCEPTABLE, desc = "read before all writes")
@Outcome(id = "1.0, 1.0, 2.0", expect = Expect.ACCEPTABLE, desc = "write all before read")
@Outcome(id = "1.0, 1.0, 1.0", expect = Expect.ACCEPTABLE, desc = "read after first write")
@Outcome(id = "0.0, 0.0, 1.0", expect = Expect.FORBIDDEN, desc = "read first write mid snapshot")
@State
public class HistogramConsistencyTest {

    DistributionStatisticConfig config = new DistributionStatisticConfig.Builder().serviceLevelObjectives(4, 5, 10).build().merge(DistributionStatisticConfig.DEFAULT);

    Histogram histogram = new TimeWindowFixedBoundaryHistogram(Clock.SYSTEM, config, false);

    @Actor
    public void record1() {
        histogram.recordDouble(1);
        histogram.recordDouble(7);
    }

    @Actor
    public void readSnapshot(DDD_Result result) {
        CountAtBucket[] countAtBuckets = histogram.takeSnapshot(0, 0, 0).histogramCounts();
        result.r1 = countAtBuckets[0].count();
        result.r2 = countAtBuckets[1].count();
        result.r3 = countAtBuckets[2].count();
    }

}
