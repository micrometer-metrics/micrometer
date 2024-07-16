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
package io.micrometer.observation.tck;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An {@link ObservationHandler} that validates the order of events of an Observation (for
 * example stop should be called after start) and with a validation message and the
 * original context, it publishes the events of these invalid scenarios to the
 * {@link Consumer} of your choice.
 *
 * @author Jonatan Ivanov
 */
class ObservationValidator implements ObservationHandler<Context> {

    private final Consumer<ValidationResult> consumer;

    private final Predicate<Context> supportsContextPredicate;

    ObservationValidator() {
        this(ObservationValidator::throwInvalidObservationException);
    }

    ObservationValidator(Consumer<ValidationResult> consumer) {
        this(consumer, context -> true);
    }

    ObservationValidator(Consumer<ValidationResult> consumer, Predicate<Context> supportsContextPredicate) {
        this.consumer = consumer;
        this.supportsContextPredicate = supportsContextPredicate;
    }

    @Override
    public void onStart(Context context) {
        Status status = context.get(Status.class);
        if (status != null) {
            consumer.accept(new ValidationResult("Invalid start: Observation has already been started", context));
        }
        else {
            context.put(Status.class, new Status());
        }
    }

    @Override
    public void onError(Context context) {
        checkIfObservationWasStartedButNotStopped("Invalid error signal", context);
    }

    @Override
    public void onEvent(Event event, Context context) {
        checkIfObservationWasStartedButNotStopped("Invalid event signal", context);
    }

    @Override
    public void onScopeOpened(Context context) {
        checkIfObservationWasStartedButNotStopped("Invalid scope opening", context);
    }

    @Override
    public void onScopeClosed(Context context) {
        checkIfObservationWasStartedButNotStopped("Invalid scope closing", context);
    }

    @Override
    public void onScopeReset(Context context) {
        checkIfObservationWasStartedButNotStopped("Invalid scope resetting", context);
    }

    @Override
    public void onStop(Context context) {
        Status status = checkIfObservationWasStartedButNotStopped("Invalid stop", context);
        if (status != null) {
            status.markStopped();
        }
    }

    @Override
    public boolean supportsContext(Context context) {
        return supportsContextPredicate.test(context);
    }

    @Nullable
    private Status checkIfObservationWasStartedButNotStopped(String prefix, Context context) {
        Status status = context.get(Status.class);
        if (status == null) {
            consumer.accept(new ValidationResult(prefix + ": Observation has not been started yet", context));
        }
        else if (status.isStopped()) {
            consumer.accept(new ValidationResult(prefix + ": Observation has already been stopped", context));
        }

        return status;
    }

    private static void throwInvalidObservationException(ValidationResult validationResult) {
        throw new InvalidObservationException(validationResult.getMessage(), validationResult.getContext());
    }

    static class ValidationResult {

        private final String message;

        private final Context context;

        ValidationResult(String message, Context context) {
            this.message = message;
            this.context = context;
        }

        String getMessage() {
            return message;
        }

        Context getContext() {
            return context;
        }

        @Override
        public String toString() {
            return getMessage() + " - " + getContext();
        }

    }

    static class Status {

        private boolean stopped = false;

        boolean isStopped() {
            return stopped;
        }

        void markStopped() {
            stopped = true;
        }

    }

}
