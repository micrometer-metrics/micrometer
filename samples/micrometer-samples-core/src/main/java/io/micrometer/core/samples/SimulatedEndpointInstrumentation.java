/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulatedEndpointInstrumentation {

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();

        Timer e1Success = Timer.builder("http.server.requests")
            .tags("uri", "/api/bar")
            .tags("response", "200")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        Timer e2Success = Timer.builder("http.server.requests")
            .tags("uri", "/api/foo")
            .tags("response", "200")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        Timer e1Fail = Timer.builder("http.server.requests")
            .tags("uri", "/api/bar")
            .tags("response", "500")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        Timer e2Fail = Timer.builder("http.server.requests")
            .tags("uri", "/api/foo")
            .tags("response", "500")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        RandomEngine r = new MersenneTwister64(0);
        Normal incomingRequests = new Normal(0, 1, r);
        Normal successOrFail = new Normal(0, 1, r);

        Normal duration = new Normal(250, 50, r);
        Normal duration2 = new Normal(250, 50, r);

        AtomicInteger latencyForThisSecond = new AtomicInteger(duration.nextInt());
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> latencyForThisSecond.set(duration.nextInt())).subscribe();

        AtomicInteger latencyForThisSecond2 = new AtomicInteger(duration2.nextInt());
        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> latencyForThisSecond2.set(duration2.nextInt())).subscribe();

        // the potential for an "incoming request" every 10 ms
        Flux.interval(Duration.ofMillis(10)).doOnEach(d -> {
            // are we going to receive a request for /api/foo?
            if (incomingRequests.nextDouble() + 0.4 > 0) {
                if (successOrFail.nextDouble() + 0.8 > 0) {
                    // pretend the request took some amount of time, such that the time is
                    // distributed normally with a mean of 250ms
                    e1Success.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS);
                }
                else {
                    e1Fail.record(latencyForThisSecond.get(), TimeUnit.MILLISECONDS);
                }
            }
        }).subscribe();

        // the potential for an "incoming request" every 1 ms
        Flux.interval(Duration.ofMillis(1)).doOnEach(d -> {
            // are we going to receive a request for /api/bar?
            if (incomingRequests.nextDouble() + 0.4 > 0) {
                if (successOrFail.nextDouble() + 0.8 > 0) {
                    // pretend the request took some amount of time, such that the time is
                    // distributed normally with a mean of 250ms
                    e2Success.record(latencyForThisSecond2.get(), TimeUnit.MILLISECONDS);
                }
                else {
                    e2Fail.record(latencyForThisSecond2.get(), TimeUnit.MILLISECONDS);
                }
            }
        }).blockLast();
    }

}
