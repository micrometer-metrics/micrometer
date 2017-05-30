package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import com.netflix.servo.publish.*;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.servo.ServoRegistry;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class BucketFunctionSample {
    private static MetricObserver rateTransform(MetricObserver observer) {
        final long pollInterval = 10;
        final long heartbeat = 2 * pollInterval;
        return new CounterToRateMetricTransform(observer, heartbeat, TimeUnit.SECONDS);
    }

    private static MetricObserver createFileObserver(File dir) {
        if (!dir.mkdirs() && !dir.isDirectory())
            throw new IllegalStateException("failed to create metrics directory: " + dir);
//        return rateTransform(new FileMetricObserver("servo-example", dir));
        return new FileMetricObserver("servo-example", dir);
    }

    public static void main(String[] args) throws InterruptedException {
        PollRunnable task = new PollRunnable(new MonitorRegistryMetricPoller(),
                BasicMetricFilter.MATCH_ALL,
                true,
                Collections.singletonList(
                        createFileObserver(new File("metrics"))
                )
        );

        PollScheduler scheduler = PollScheduler.getInstance();
        scheduler.start();
        scheduler.addPoller(task, 10, TimeUnit.SECONDS);

        ServoRegistry registry = new ServoRegistry();
        Spectator.globalRegistry().add(registry);

        RandomEngine r = new MersenneTwister64(0);
        Normal dist = new Normal(25000, 1000, r);

//        BucketTimer timer = BucketTimer.get(registry, registry.createId("timer"), BucketFunctions.latency(200, TimeUnit.MILLISECONDS));
        PercentileTimer pTimer = PercentileTimer.get(registry, registry.createId("pTimer"));

        while(true) {
            long sample = (long) Math.max(0, dist.nextDouble());
//            timer.record(sample, TimeUnit.MILLISECONDS);
            pTimer.record(sample, TimeUnit.MILLISECONDS);
        }
    }
}
