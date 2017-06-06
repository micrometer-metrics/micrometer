package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.atlas.AtlasRegistry;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.spectator.SpectatorMeterRegistry;

import java.util.concurrent.TimeUnit;

public class AtlasSample {
    public static void main(String[] args) throws InterruptedException {
        AtlasRegistry atlasRegistry = new AtlasRegistry(Clock.SYSTEM, k -> null);
        atlasRegistry.start();

        MeterRegistry meterRegistry = new SpectatorMeterRegistry(atlasRegistry);

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(25000, 1000, r);

        Timer timer = meterRegistry.timer("timer");

        while(true) {
            long sample = (long) Math.max(0, dist.nextDouble());
            timer.record(sample, TimeUnit.MILLISECONDS);
        }
    }
}
