/*
 * Copyright 2021 VMware, Inc.
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

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.instrument.observation.ObservationHandler;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.observation.ObservationRegistry;
import io.micrometer.api.instrument.observation.TimerObservationRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class ObservationHandlerSample {
    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private static final ObservationRegistry observationRegistry = new TimerObservationRegistry(registry);

    public static void main(String[] args) throws InterruptedException {
        observationRegistry.config().observationHandler(new SampleHandler());
        observationRegistry.config().observationPredicate((s, context) -> {
            boolean observationEnabled = !"sample.ignored".equals(s);
            if (!observationEnabled) {
                System.out.println("Ignoring sample.ignored");
            }
            return observationEnabled;
        });

        Observation observation = observationRegistry.observation("sample.operation", new CustomContext())
                .displayName("CALL sampleOperation")
                .lowCardinalityTag("a", "1")
                .highCardinalityTag("time", Instant.now().toString())
                .start();

        try (Observation.Scope scope = observation.openScope()) {
            Thread.sleep(1_000);
            observation.error(new IOException("simulated"));
        }
        observation.stop();

        observationRegistry.start("sample.operation").stop();
        observationRegistry.start("sample.operation", new UnsupportedHandlerContext()).stop();

        observationRegistry.start("sample.ignored", new CustomContext()).stop();

        System.out.println();
        System.out.println(registry.scrape());
    }

    static class SampleHandler implements ObservationHandler<CustomContext> {
        @Override
        public void onStart(CustomContext context) {
            if (context.getName().equals("sample.ignored")) {
                throw new AssertionError("Boom!");
            }
            System.out.println("start: " + context);
        }

        @Override
        public void onError(CustomContext context) {
            System.out.println("error: " + context.getError() + " " + context.getError());
        }

        @Override
        public void onScopeOpened(CustomContext context) {
            System.out.println("scope-opened: " + context);
        }

        @Override
        public void onScopeClosed(CustomContext context) {
            System.out.println("scope-closed: " + context);
        }

        @Override
        public void onStop(CustomContext context) {
            System.out.println("stop: " + context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof CustomContext;
        }
    }

    static class CustomContext extends Observation.Context {
        private final UUID uuid = UUID.randomUUID();

        @Override
        public Tags getLowCardinalityTags() {
            return Tags.of("status", "ok");
        }

        @Override
        public Tags getHighCardinalityTags() {
            return Tags.of("userId", uuid.toString());
        }

        @Override
        public String toString() {
            return "CustomHandlerContext{" + uuid + '}';
        }
    }

    static class UnsupportedHandlerContext extends Observation.Context {
        @Override
        public String toString() {
            return "sorry";
        }
    }
}
