package io.micrometer.core.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.stats.hist.CumulativeHistogram;
import io.micrometer.core.instrument.stats.quantile.CKMSQuantiles;
import io.micrometer.core.samples.utils.Registries;

import static io.micrometer.core.instrument.stats.hist.CumulativeHistogram.linear;

/**
 * Demonstrates how a histogram can also contain quantiles.
 *
 * @author Jon Schneider
 */
public class HistogramSample {
    public static void main(String[] args) throws InterruptedException {
        PrometheusMeterRegistry meterRegistry = Registries.prometheus();

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(100, 50, r);

        DistributionSummary hist = meterRegistry.summaryBuilder("hist")
                .histogram(CumulativeHistogram.buckets(linear(0, 10, 20)))
                .quantiles(CKMSQuantiles
                        .quantile(0.95, 0.01)
                        .quantile(0.5, 0.05)
                        .create())
                .create();

        //noinspection InfiniteLoopStatement
        while(true) {
            Thread.sleep(10);
            long sample = (long) Math.max(0, dist.nextDouble());
            hist.record(sample);
        }
    }
}
