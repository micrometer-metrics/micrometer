package io.micrometer.spring.samples.components;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Configuration
public class RandomSampler {
    private RandomEngine r = new MersenneTwister64(0);
    private Normal dist = new Normal(25000.0D, 1000.0D, r);
    private final List<DistributionSummary> summary;

    public RandomSampler(MeterRegistry registry) {
        this.summary = IntStream.range(0, 1)
                .mapToObj(n -> registry.summary("random" + n))
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 10)
    public void sampleRandom() {
        summary.forEach(s -> s.record(dist.nextDouble()));
    }
}