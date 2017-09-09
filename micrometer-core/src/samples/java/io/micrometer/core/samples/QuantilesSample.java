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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.quantile.CKMSQuantiles;
import io.micrometer.core.instrument.stats.quantile.Frugal2UQuantiles;
import io.micrometer.core.instrument.stats.quantile.GKQuantiles;
import io.micrometer.core.instrument.stats.quantile.WindowSketchQuantiles;
import io.micrometer.core.samples.utils.SampleRegistries;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the four quantile algorithms.
 *
 * @author Jon Schneider
 */
public class QuantilesSample {
    public static void main(String[] args) {
        MeterRegistry registry = SampleRegistries.prometheus();

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        Timer ckmsTimer = Timer.builder("random.ckms")
                .quantiles(CKMSQuantiles
                        .quantile(0.5, 0.05)
                        .quantile(0.95, 0.01)
                        .create())
                .register(registry);

        Timer frugalTimer = Timer.builder("random.frugal")
                .quantiles(Frugal2UQuantiles
                        .quantile(0.5, 10)
                        .quantile(0.95, 10)
                        .create())
                .register(registry);

        Timer gkTimer = Timer.builder("random.gk")
                .quantiles(GKQuantiles.quantiles(0.5, 0.95).create())
                .register(registry);

        Timer windowTimer = Timer.builder("random.window")
                .quantiles(WindowSketchQuantiles.quantiles(0.5, 0.95).create())
                .register(registry);

        //noinspection InfiniteLoopStatement
        while(true) {
            long sample = (long) Math.max(0, dist.nextDouble());
            ckmsTimer.record(sample, TimeUnit.SECONDS);
            frugalTimer.record(sample, TimeUnit.SECONDS);
            gkTimer.record(sample, TimeUnit.SECONDS);
            windowTimer.record(sample, TimeUnit.SECONDS);
        }
    }
}
