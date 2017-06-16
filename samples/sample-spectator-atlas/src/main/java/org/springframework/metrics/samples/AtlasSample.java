package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.export.atlas.EnableAtlasMetrics;
import org.springframework.metrics.instrument.DistributionSummary;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Arrays;

@SpringBootApplication
@EnableAtlasMetrics
@EnableScheduling
public class AtlasSample {
    public static void main(String[] args) {
        SpringApplication.run(AtlasSample.class, args);
    }
}

@RestController
class PersonController {
    private List<String> people = Arrays.asList("mike", "suzy");

    @GetMapping("/api/people")
    @Timed
    public List<String> allPeople() {
        return people;
    }
}

@Configuration
class RandomSampler {
    private RandomEngine r = new MersenneTwister64(0);
    private Normal dist = new Normal(25000.0D, 1000.0D, r);
    private final DistributionSummary summary;

    public RandomSampler(MeterRegistry registry) {
        this.summary = registry.summary("random");
    }

    @Scheduled(fixedRate = 10)
    public void sampleRandom() {
        summary.record(dist.nextDouble());
    }
}