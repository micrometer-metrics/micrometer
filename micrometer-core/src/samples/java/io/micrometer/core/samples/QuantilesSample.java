package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.stats.quantile.CKMSQuantiles;
import io.micrometer.core.instrument.stats.quantile.Frugal2UQuantiles;
import io.micrometer.core.instrument.stats.quantile.GKQuantiles;
import io.micrometer.core.instrument.stats.quantile.WindowSketchQuantiles;
import io.micrometer.core.samples.utils.Registries;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the four quantile algorithms.
 *
 * @author Jon Schneider
 */
public class QuantilesSample {
    public static void main(String[] args) {
        PrometheusMeterRegistry registry = Registries.prometheus();

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        Timer ckmsTimer = registry.timerBuilder("random_ckms")
                .quantiles(CKMSQuantiles
                        .quantile(0.5, 0.05)
                        .quantile(0.95, 0.01)
                        .create())
                .create();

        Timer frugalTimer = registry.timerBuilder("random_frugal")
                .quantiles(Frugal2UQuantiles
                        .quantile(0.5, 10)
                        .quantile(0.95, 10)
                        .create())
                .create();

        Timer gkTimer = registry.timerBuilder("random_gk")
                .quantiles(GKQuantiles.quantiles(0.5, 0.95).create())
                .create();

        Timer windowTimer = registry.timerBuilder("random_window")
                .quantiles(WindowSketchQuantiles.quantiles(0.5, 0.95).create())
                .create();

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
