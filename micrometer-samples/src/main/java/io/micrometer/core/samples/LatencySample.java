package io.micrometer.core.samples;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.samples.utils.SampleRegistries;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class LatencySample {
    public static void main(String[] args) {
        new LatencySample().run();
    }

    private MeterRegistry registry = SampleRegistries.jmx();
    private Random r = new Random();
    private Timer timer = Timer.builder("request")
        .publishPercentileHistogram()
        .register(registry);

    void bar() {
        registry.timer("bar.latency")
            .record(() -> {
                // do something here
            });
    }

    void run() {


        Flux.interval(Duration.ofMillis(1))
            .doOnEach(n -> recordGaussian(10))
            .subscribe();

        Flux.interval(Duration.ofSeconds(1))
            .doOnEach(n -> recordGaussian(300))
            .blockLast();
    }

    private void recordGaussian(long center) {
        timer.record(simulatedLatency(center), TimeUnit.MILLISECONDS);
    }

    private long simulatedLatency(long center) {
        return (long) (r.nextGaussian() * 10) + center;
    }
}
