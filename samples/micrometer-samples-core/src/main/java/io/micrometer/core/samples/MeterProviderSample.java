/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.samples.utils.SampleConfig;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class MeterProviderSample {

    public static void main(String[] args) {
        MeterRegistry registry = SampleConfig.myMonitoringSystem();
        MeterProvider<Timer> timerProvider = Timer.builder("job.execution")
            .tag("job.name", "job")
            .withRegistry(registry);

        Flux.interval(Duration.ofSeconds(1)).doOnEach(d -> {
            Timer.Sample sample = Timer.start(registry);
            Result result = new Job().execute();
            sample.stop(timerProvider.withTags("status", result.status()));
        }).blockLast();
    }

    static class Job {

        Result execute() {
            try {
                Thread.sleep((long) (Math.random() * 100 + 100));
            }
            catch (InterruptedException e) {
                // ignored
            }

            return new Result(Math.random() > 0.2 ? "SUCCESS" : "FAILED");
        }

    }

    static class Result {

        final String status;

        Result(String status) {
            this.status = status;
        }

        String status() {
            return status;
        }

    }

}
