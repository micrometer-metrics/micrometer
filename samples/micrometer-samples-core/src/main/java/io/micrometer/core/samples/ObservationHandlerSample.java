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

import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.ObservationConvention;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class ObservationHandlerSample {

    private static final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static final ObservationRegistry observationRegistry = ObservationRegistry.create();

    public static void main(String[] args) throws InterruptedException {
        observationRegistry.observationConfig()
            .observationHandler(new ObservationTextPublisher())
            .observationHandler(new DefaultMeterObservationHandler(registry));
        observationRegistry.observationConfig()
            .observationConvention(new CustomObservationConvention())
            .observationPredicate(new IgnoringObservationPredicate());

        Observation observation = Observation
            .createNotStarted("sample.operation", CustomContext::new, observationRegistry)
            .contextualName("CALL sampleOperation")
            .lowCardinalityKeyValue("a", "1")
            .highCardinalityKeyValue("time", Instant.now().toString())
            .observationConvention(new CustomLocalObservationConvention())
            .start();

        try (Observation.Scope scope = observation.openScope()) {
            Thread.sleep(1_000);
            observation.event(Observation.Event.of("custom.event", "Custom " + UUID.randomUUID()));
            observation.error(new IOException("simulated"));
        }

        System.out.println("--- Meters before stop:");
        System.out.println(registry.getMetersAsString());
        System.out.println("---");

        observation.stop();

        Observation.start("sample.no-context", observationRegistry).stop();
        Observation.start("sample.unsupported", UnsupportedContext::new, observationRegistry).stop();
        Observation.start("sample.ignored", CustomContext::new, observationRegistry).stop();

        System.out.println("--- Meters:");
        System.out.println(registry.getMetersAsString());
    }

    static class CustomContext extends Observation.Context {

        private final UUID uuid = UUID.randomUUID();

    }

    static class CustomObservationConvention implements GlobalObservationConvention<CustomContext> {

        @Override
        public KeyValues getLowCardinalityKeyValues(CustomContext context) {
            return KeyValues.of("className", context.getClass().getSimpleName());
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(CustomContext context) {
            return KeyValues.of("userId", context.uuid.toString());
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof CustomContext;
        }

    }

    static class CustomLocalObservationConvention implements ObservationConvention<CustomContext> {

        @Override
        public KeyValues getLowCardinalityKeyValues(CustomContext context) {
            return KeyValues.of("localClassName", context.getClass().getSimpleName());
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(CustomContext context) {
            return KeyValues.of("localUserId", context.uuid.toString());
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
