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
package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.junit.jupiter.api.Test;

import static io.micrometer.observation.Observation.NOOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ObservationRegistry}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 */
class ObservationRegistryTest {

    private final ObservationRegistry registry = ObservationRegistry.create();

    @Test
    void openingScopeShouldSetSampleAsCurrent() {
        registry.observationConfig().observationHandler(c -> true);
        Observation sample = Observation.start("test.timer", registry);
        Observation.Scope scope = sample.openScope();

        assertThat(registry.getCurrentObservation()).isSameAs(sample);

        scope.close();
        sample.stop();

        assertThat(registry.getCurrentObservation()).isNull();
    }

    @Test
    void observationHandlersShouldBeAddedToTheRegistry() {
        ObservationHandler<?> handler1 = mock(ObservationHandler.class);
        ObservationHandler<?> handler2 = mock(ObservationHandler.class);

        registry.observationConfig().observationHandler(handler1);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactly(handler1);

        registry.observationConfig().observationHandler(handler2);
        assertThat(registry.observationConfig().getObservationHandlers()).containsExactlyInAnyOrder(handler1, handler2);
    }

    @Test
    void observationShouldBeNoopWhenPredicateApplicable() {
        registry.observationConfig().observationPredicate((name, context) -> !name.equals("test.timer"));

        Observation sample = Observation.start("test.timer", registry);

        assertThat(sample).isSameAs(NOOP);
    }

    @Test
    void observationShouldBeNoopWhenNullRegistry() {
        assertThat(Observation.start("test.timer", null)).isSameAs(NOOP);
        assertThat(Observation.start("test.timer", Observation.Context::new, null)).isSameAs(NOOP);
        assertThat(Observation.createNotStarted("test.timer", null)).isSameAs(NOOP);
        assertThat(Observation.createNotStarted("test.timer", Observation.Context::new, null)).isSameAs(NOOP);
    }

    @Test
    void observationShouldNotBeNoopWhenNonNullRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(c -> true);
        assertThat(Observation.start("test.timer", registry)).isInstanceOf(SimpleObservation.class);
        assertThat(Observation.start("test.timer", Observation.Context::new, registry))
            .isInstanceOf(SimpleObservation.class);
        assertThat(Observation.createNotStarted("test.timer", registry)).isInstanceOf(SimpleObservation.class);
        assertThat(Observation.createNotStarted("test.timer", Observation.Context::new, registry))
            .isInstanceOf(SimpleObservation.class);
    }

    @Test
    void observationShouldWorkWithConventions() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(c -> true);
        // Define a convention
        MessagingConvention messagingConvention = new OurCompanyStandardMessagingConvention();

        Observation.Context myContext = new MessagingContext().put("foo", "hello");
        // Observation convention wants to use a MessagingConvention
        MessagingObservationConvention messagingObservationConvention = new MessagingObservationConvention(
                messagingConvention);

        Observation.createNotStarted("observation", () -> myContext, registry)
            .observationConvention(messagingObservationConvention)
            .start()
            .stop();

        then(myContext.getLowCardinalityKeyValues()
            .stream()
            .filter(keyValue -> keyValue.getKey().equals("baz"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No <baz> key value found"))
            .getValue()).isEqualTo("hello bar");
        then(myContext.getName()).isEqualTo("new name");
    }

    static class MessagingContext extends Observation.Context {

    }

    static class MessagingObservationConvention implements ObservationConvention<MessagingContext> {

        private final MessagingConvention messagingConvention;

        MessagingObservationConvention(MessagingConvention messagingConvention) {
            this.messagingConvention = messagingConvention;
        }

        // Here we override the default "observation" name
        @Override
        public String getName() {
            return "new name";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(MessagingContext context) {
            return KeyValues.of(this.messagingConvention.queueName(context.get("foo")));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof MessagingContext;
        }

    }

    interface MessagingConvention extends KeyValuesConvention {

        KeyValue queueName(String foo);

    }

    static class OurCompanyStandardMessagingConvention implements MessagingConvention {

        // In our standard the queue name should be registered under "baz" tag key
        @Override
        public KeyValue queueName(String messagePayload) {
            return KeyValue.of("baz", messagePayload + " bar");
        }

    }

}
