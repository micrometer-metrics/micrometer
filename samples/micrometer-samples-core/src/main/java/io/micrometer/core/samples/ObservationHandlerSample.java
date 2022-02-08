/*
 * Copyright 2022 VMware, Inc.
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
import io.micrometer.api.instrument.simple.SimpleMeterRegistry;

public class ObservationHandlerSample {
    private static final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    public static void main(String[] args) throws InterruptedException {
        registry.withTimerObservationHandler()
                .observationConfig()
                    .observationHandler(new SampleHandler())
                    .observationPredicate((s, context) -> {
                        boolean observationEnabled = !"sample.ignored".equals(s);
                        if (!observationEnabled) {
                            System.out.println("Ignoring sample.ignored");
                        }
                        return observationEnabled;
                    });

        Observation observation = Observation.createNotStarted("sample.operation", new CustomContext(), registry)
                .contextualName("CALL sampleOperation")
                .lowCardinalityTag("a", "1")
                .highCardinalityTag("time", Instant.now().toString())
                .start();

        try (Observation.Scope scope = observation.openScope()) {
            Thread.sleep(1_000);
            observation.error(new IOException("simulated"));
        }
        observation.stop();

        Observation.start("sample.no-context", registry).stop();
        Observation.start("sample.unsupported", new UnsupportedHandlerContext(), registry).stop();
        Observation.start("sample.ignored", new CustomContext(), registry).stop();

        System.out.println();
        System.out.println(registry.getMetersAsString());
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
            System.out.println("error: " + context.getError() + " " + context);
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
