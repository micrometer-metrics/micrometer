/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.CKMSQuantiles;
import io.micrometer.core.samples.utils.SampleRegistries;

/**
 * Demonstrates how a histogram can also contain quantiles.
 *
 * @author Jon Schneider
 */
public class HistogramSample {
    public static void main(String[] args) throws InterruptedException {
        MeterRegistry registry = SampleRegistries.prometheus();

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        DistributionSummary hist = DistributionSummary.builder("hist")
                .histogram(Histogram.linear(0, 10, 20))
                .quantiles(CKMSQuantiles
                        .quantile(0.95, 0.01)
                        .quantile(0.5, 0.05)
                        .create())
                .register(registry);

        //noinspection InfiniteLoopStatement
        while(true) {
            Thread.sleep(10);
            long sample = (long) Math.max(0, dist.nextDouble());
            hist.record(sample);
        }
    }
}
