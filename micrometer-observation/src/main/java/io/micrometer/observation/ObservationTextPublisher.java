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
package io.micrometer.observation;

import io.micrometer.common.util.internal.logging.InternalLoggerFactory;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An {@link ObservationHandler} that converts the context to text and publishes it to the
 * {@link Consumer} of your choice.
 *
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
public class ObservationTextPublisher implements ObservationHandler<Observation.Context> {

    private final Consumer<String> consumer;

    private final Predicate<Observation.Context> supportsContextPredicate;

    private final Function<Observation.Context, String> converter;

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer}.
     */
    public ObservationTextPublisher() {
        this(InternalLoggerFactory.getInstance(ObservationTextPublisher.class)::info, context -> true, String::valueOf);
    }

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer}.
     * @param consumer Where to publish the context as text
     */
    public ObservationTextPublisher(Consumer<String> consumer) {
        this(consumer, context -> true, String::valueOf);
    }

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer} if
     * the {@link Predicate} returns true.
     * @param consumer Where to publish the context as text
     * @param supportsContextPredicate Whether the publisher should support the given
     * context
     */
    public ObservationTextPublisher(Consumer<String> consumer,
            Predicate<Observation.Context> supportsContextPredicate) {
        this(consumer, supportsContextPredicate, String::valueOf);
    }

    /**
     * Creates a publisher that sends the context as text to the given {@link Consumer} if
     * the {@link Predicate} returns true.
     * @param consumer Where to publish the context as text
     * @param supportsContextPredicate Whether the publisher should support the given
     * context
     * @param converter Converts the {@link Observation.Context} to a {@link String}
     */
    public ObservationTextPublisher(Consumer<String> consumer, Predicate<Observation.Context> supportsContextPredicate,
            Function<Observation.Context, String> converter) {
        this.consumer = consumer;
        this.supportsContextPredicate = supportsContextPredicate;
        this.converter = converter;
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
    public void onEvent(Observation.Event event, Observation.Context context) {
        publishUnformatted(String.format("%5s - %s, %s", "EVENT", event, converter.apply(context)));
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
        publishUnformatted(String.format("%5s - %s", event, converter.apply(context)));
    }

    private void publishUnformatted(String event) {
        this.consumer.accept(event);
    }

}
