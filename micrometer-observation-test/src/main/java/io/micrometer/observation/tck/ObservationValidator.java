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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.NullObservation.NullContext;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.InvalidObservationException.EventName;
import io.micrometer.observation.tck.InvalidObservationException.HistoryElement;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.micrometer.observation.tck.TestObservationRegistry.Capability;
import static io.micrometer.observation.tck.TestObservationRegistry.Capability.OBSERVATIONS_WITH_THE_SAME_NAME_SHOULD_HAVE_THE_SAME_SET_OF_LOW_CARDINALITY_KEYS;

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

    private final Map<String, Set<String>> lowCardinalityKeys;

    private final Set<Capability> capabilities;

    ObservationValidator(Set<Capability> capabilities) {
        this.consumer = ObservationValidator::throwInvalidObservationException;
        this.supportsContextPredicate = context -> !(context instanceof NullContext);
        this.lowCardinalityKeys = new HashMap<>();
        this.capabilities = capabilities;
    }

    @Override
    public void onStart(Context context) {
        addHistoryElement(context, EventName.START);
        Status status = context.get(Status.class);
        if (status != null) {
            consumer.accept(new ValidationResult(
                    "Invalid start: Observation '" + context.getName() + "' has already been started", context));
        }
        else {
            context.put(Status.class, new Status());
        }
    }

    @Override
    public void onError(Context context) {
        addHistoryElement(context, EventName.ERROR);
        checkIfObservationWasStartedButNotStopped("Invalid error signal", context);
    }

    @Override
    public void onEvent(Event event, Context context) {
        addHistoryElement(context, EventName.EVENT);
        checkIfObservationWasStartedButNotStopped("Invalid event signal", context);
    }

    @Override
    public void onScopeOpened(Context context) {
        addHistoryElement(context, EventName.SCOPE_OPEN);
        // In some cases (Reactor) scope open can happen after the observation is stopped
        checkIfObservationWasStarted("Invalid scope opening", context);
    }

    @Override
    public void onScopeClosed(Context context) {
        addHistoryElement(context, EventName.SCOPE_CLOSE);
        // In some cases (Reactor) scope close can happen after the observation is stopped
        checkIfObservationWasStarted("Invalid scope closing", context);
    }

    @Override
    public void onScopeReset(Context context) {
        addHistoryElement(context, EventName.SCOPE_RESET);
        // In some cases (Reactor) scope reset can happen after the observation is stopped
        checkIfObservationWasStarted("Invalid scope resetting", context);
    }

    @Override
    public void onStop(Context context) {
        addHistoryElement(context, EventName.STOP);
        Status status = checkIfObservationWasStartedButNotStopped("Invalid stop", context);
        if (status != null) {
            status.markStopped();
        }
        if (capabilities.contains(OBSERVATIONS_WITH_THE_SAME_NAME_SHOULD_HAVE_THE_SAME_SET_OF_LOW_CARDINALITY_KEYS)) {
            checkIfObservationsWithTheSameNameHaveTheSameSetOfLowCardinalityKeys(context);
        }
    }

    @Override
    public boolean supportsContext(Context context) {
        return supportsContextPredicate.test(context);
    }

    private void addHistoryElement(Context context, EventName eventName) {
        History history = context.computeIfAbsent(History.class, clazz -> new History());
        history.addHistoryElement(eventName);
    }

    private @Nullable Status checkIfObservationWasStarted(String prefix, Context context) {
        Status status = context.get(Status.class);
        if (status == null) {
            consumer.accept(new ValidationResult(
                    prefix + ": Observation '" + context.getName() + "' has not been started yet", context));
        }

        return status;
    }

    private @Nullable Status checkIfObservationWasStartedButNotStopped(String prefix, Context context) {
        Status status = checkIfObservationWasStarted(prefix, context);
        if (status != null && status.isStopped()) {
            consumer.accept(new ValidationResult(
                    prefix + ": Observation '" + context.getName() + "' has already been stopped", context));
        }

        return status;
    }

    private void checkIfObservationsWithTheSameNameHaveTheSameSetOfLowCardinalityKeys(Context context) {
        if (lowCardinalityKeys.containsKey(context.getName())) {
            Set<String> existingKeys = lowCardinalityKeys.get(context.getName());
            Set<String> currentKeys = getLowCardinalityKeys(context);
            if (!existingKeys.equals(currentKeys)) {
                String message = "Metrics backends may require that all observations with the same name have the same"
                        + " set of low cardinality keys. There is already an existing observation named '"
                        + context.getName() + "' containing keys [" + String.join(", ", existingKeys)
                        + "]. The observation you are attempting to register" + " has keys ["
                        + String.join(", ", currentKeys) + "].";
                throw new InvalidObservationException(message, context);
            }
        }
        else {
            lowCardinalityKeys.put(Objects.requireNonNull(context.getName()), getLowCardinalityKeys(context));
        }
    }

    private Set<String> getLowCardinalityKeys(Context context) {
        return getKeys(context.getLowCardinalityKeyValues());
    }

    private Set<String> getKeys(KeyValues keyValues) {
        return keyValues.stream().map(KeyValue::getKey).collect(Collectors.toSet());
    }

    private static void throwInvalidObservationException(ValidationResult validationResult) {
        History history = validationResult.getContext().getOrDefault(History.class, History::new);
        throw new InvalidObservationException(validationResult.getMessage(), validationResult.getContext(),
                history.getHistoryElements());
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

    static class History {

        private final List<HistoryElement> historyElements = new ArrayList<>();

        private void addHistoryElement(EventName eventName) {
            historyElements.add(new HistoryElement(eventName));
        }

        List<HistoryElement> getHistoryElements() {
            return Collections.unmodifiableList(historyElements);
        }

    }

}
