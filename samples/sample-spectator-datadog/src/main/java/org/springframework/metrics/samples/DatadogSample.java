package org.springframework.metrics.samples;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister64;
import cern.jet.random.engine.RandomEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.metrics.annotation.Timed;
import org.springframework.metrics.export.datadog.DatadogConfig;
import org.springframework.metrics.export.datadog.EnableDatadogMetrics;
import org.springframework.metrics.instrument.Counter;
import org.springframework.metrics.instrument.DistributionSummary;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@EnableDatadogMetrics
@EnableScheduling
public class DatadogSample {
    public static void main(String[] args) {
        SpringApplication.run(DatadogSample.class, args);
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