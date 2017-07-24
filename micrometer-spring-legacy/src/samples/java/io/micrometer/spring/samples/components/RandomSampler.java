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