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


import io.micrometer.common.KeyValue;

/**
 * No-op implementation of {@link Observation} so that we can disable the instrumentation logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 *
 * @since 1.10.0
 */
final class NoopObservation implements Observation {
    /**
     * Instance of {@link NoopObservation}.
     */
    static final NoopObservation INSTANCE = new NoopObservation();

    private NoopObservation() {
    }

    @Override
    public Observation contextualName(String contextualName) {
        return this;
    }

    @Override
    public Observation lowCardinalityKeyValue(KeyValue keyValue) {
        return this;
    }

    @Override
    public Observation lowCardinalityKeyValue(String key, String value) {
        return this;
    }

    @Override
    public Observation highCardinalityKeyValue(KeyValue keyValue) {
        return this;
    }

    @Override
    public Observation highCardinalityKeyValue(String key, String value) {
        return this;
    }

    @Override
    public Observation keyValuesProvider(KeyValuesProvider<?> keyValuesProvider) {
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        return this;
    }

    @Override
    public Observation start() {
        return this;
    }

    @Override
    public void stop() {
    }

    @Override
    public Scope openScope() {
        return NoOpScope.INSTANCE;
    }

    /**
     * Scope that does nothing.
     */
    static final class NoOpScope implements Scope {
        /**
         * Instance of {@link NoOpScope}.
         */
        public static final Scope INSTANCE = new NoOpScope();

        private NoOpScope() {

        }

        @Override
        public Observation getCurrentObservation() {
            return NoopObservation.INSTANCE;
        }

        @Override
        public void close() {
        }
    }
}
