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
package io.micrometer.api.instrument.observation;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An {@link ObservationHandler} that converts the context to text and Publishes it to the {@link Consumer} of your choice.
 *
 * @author Jonatan Ivanov
 * @since 2.0.0
 */
public class ObservationTextPublisher implements ObservationHandler<Observation.Context> {
    private final Consumer<String> consumer;
    private final Predicate<Observation.Context> supportsContextPredicate;

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer}.
     *
     * @param consumer Where to publish the context as text
     */
    public ObservationTextPublisher(Consumer<String> consumer) {
        this(consumer, context -> true);
    }

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer} if the {@link Predicate} returns true.
     *
     * @param consumer Where to publish the context as text
     * @param supportsContextPredicate Whether the publisher should support the given context
     */
    public ObservationTextPublisher(Consumer<String> consumer, Predicate<Observation.Context> supportsContextPredicate) {
        this.consumer = consumer;
        this.supportsContextPredicate = supportsContextPredicate;
    }

    @Override
    public void onStart(Observation.Context context) {
        publish("START", context);
    }

    @Override
    public void onError(Observation.Context context) {
        publish("ERROR", context);
    }

    @Override
    public void onScopeOpened(Observation.Context context) {
        publish("OPEN", context);
    }

    @Override
    public void onScopeClosed(Observation.Context context) {
        publish("CLOSE", context);
    }

    @Override
    public void onStop(Observation.Context context) {
        publish("STOP", context);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return this.supportsContextPredicate.test(context);
    }

    private void publish(String event, Observation.Context context) {
        this.consumer.accept(String.format("%5s - %s", event, context));
    }
}
