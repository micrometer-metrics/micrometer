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

import io.micrometer.core.instrument.observation.Observation;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.observation.ObservationPredicate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class ObservationHandlerSample {
    private static final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    public static void main(String[] args) throws InterruptedException {
        registry.withTimerObservationHandler()
                .withLoggingObservationHandler()
                .observationConfig()
                    .tagsProvider(new CustomTagsProvider())
                    .observationPredicate(new IgnoringObservationPredicate());

        Observation observation = Observation.createNotStarted("sample.operation", new CustomContext(), registry)
                .contextualName("CALL sampleOperation")
                .tagsProvider(new CustomLocalTagsProvider())
                .lowCardinalityTag("a", "1")
                .highCardinalityTag("time", Instant.now().toString())
                .start();

        try (Observation.Scope scope = observation.openScope()) {
            Thread.sleep(1_000);
            observation.error(new IOException("simulated"));
        }
        observation.stop();

        Observation.start("sample.no-context", registry).stop();
        Observation.start("sample.unsupported", new UnsupportedContext(), registry).stop();
        Observation.start("sample.ignored", new CustomContext(), registry).stop();

        System.out.println("---");
        System.out.println(registry.getMetersAsString());
    }

    static class CustomContext extends Observation.Context {
        private final UUID uuid = UUID.randomUUID();
    }

    static class CustomTagsProvider implements Observation.GlobalTagsProvider<CustomContext> {
        @Override
        public Tags getLowCardinalityTags(CustomContext context) {
            return Tags.of("className", context.getClass().getSimpleName());
        }

        @Override
        public Tags getHighCardinalityTags(CustomContext context) {
            return Tags.of("userId", context.uuid.toString());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof CustomContext;
        }
    }

    static class CustomLocalTagsProvider implements Observation.TagsProvider<CustomContext> {
        @Override
        public Tags getLowCardinalityTags(CustomContext context) {
            return Tags.of("localClassName", context.getClass().getSimpleName());
        }

        @Override
        public Tags getHighCardinalityTags(CustomContext context) {
            return Tags.of("localUserId", context.uuid.toString());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof CustomContext;
        }
    }

    static class UnsupportedContext extends Observation.Context {
        @Override
        public String toString() {
            return "sorry";
        }
    }

    static class IgnoringObservationPredicate implements ObservationPredicate {
        @Override
        public boolean test(String name, Observation.Context context) {
            boolean observationIgnored = "sample.ignored".equals(name);
            if (observationIgnored) {
                System.out.println("Ignoring " + name);
            }

            return !observationIgnored;
        }
    }
}
