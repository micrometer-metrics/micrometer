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

import io.micrometer.observation.Observation;
import io.micrometer.observation.Observation.Context;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link RuntimeException} that can be thrown when an invalid {@link Observation}
 * detected.
 *
 * @author Jonatan Ivanov
 * @since 1.14.0
 */
public class InvalidObservationException extends RuntimeException {

    private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

    private final Context context;

    private final List<HistoryElement> history;

    InvalidObservationException(String message, Context context, List<HistoryElement> history) {
        super(message);
        this.context = context;
        this.history = history;
    }

    public Context getContext() {
        return context;
    }

    public List<HistoryElement> getHistory() {
        return history;
    }

    @Override
    public String toString() {
        return super.toString() + "\n"
                + history.stream().map(HistoryElement::toString).collect(Collectors.joining("\n"));
    }

    public static class HistoryElement {

        private final EventName eventName;

        private final StackTraceElement[] stackTrace;

        HistoryElement(EventName eventName) {
            this.eventName = eventName;
            StackTraceElement[] currentStackTrace = Thread.currentThread().getStackTrace();
            this.stackTrace = findRelevantStackTraceElements(currentStackTrace);
        }

        private StackTraceElement[] findRelevantStackTraceElements(StackTraceElement[] stackTrace) {
            int index = findFirstRelevantStackTraceElementIndex(stackTrace);
            if (index == -1) {
                return EMPTY_STACK_TRACE;
            }
            else {
                return Arrays.copyOfRange(stackTrace, index, stackTrace.length);
            }
        }

        private int findFirstRelevantStackTraceElementIndex(StackTraceElement[] stackTrace) {
            int index = -1;
            for (int i = 0; i < stackTrace.length; i++) {
                if (isObservationRelated(stackTrace[i])) {
                    // the first relevant StackTraceElement is after the last Observation
                    index = i + 1;
                }
            }

            return (index >= stackTrace.length) ? -1 : index;
        }

        private boolean isObservationRelated(StackTraceElement stackTraceElement) {
            String className = stackTraceElement.getClassName();
            return className.equals(Observation.class.getName())
                    || className.equals("io.micrometer.observation.SimpleObservation")
                    || className.startsWith("io.micrometer.observation.SimpleObservation$");
        }

        public EventName getEventName() {
            return eventName;
        }

        public StackTraceElement[] getStackTrace() {
            return stackTrace;
        }

        @Override
        public String toString() {
            return eventName + ": " + stackTrace[0];
        }

    }

    public enum EventName {

        START, STOP, ERROR, EVENT, SCOPE_OPEN, SCOPE_CLOSE, SCOPE_RESET

    }

}
