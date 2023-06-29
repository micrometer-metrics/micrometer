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
import io.micrometer.common.lang.Nullable;

/**
 * No-op implementation of {@link Observation} so that we can disable the instrumentation
 * logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
final class NoopObservation implements Observation {

    private static final Context CONTEXT = new Context();

    @Override
    public Observation contextualName(@Nullable String contextualName) {
        return this;
    }

    @Override
    public Observation parentObservation(@Nullable Observation parentObservation) {
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
    public Observation observationConvention(ObservationConvention<?> observationConvention) {
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        return this;
    }

    @Override
    public Observation event(Event event) {
        return this;
    }

    @Override
    public Observation start() {
        return this;
    }

    @Override
    public Context getContext() {
        return CONTEXT;
    }

    @Override
    public void stop() {
    }

    @Override
    public Scope openScope() {
        return new SimpleObservation.SimpleScope(NoopObservationRegistry.FOR_SCOPES, this);
    }

    /**
     * Scope that does nothing.
     */
    static final class NoopScope implements Scope {

        /**
         * Instance of {@link NoopScope}.
         */
        static final Scope INSTANCE = new NoopScope();

        private NoopScope() {

        }

        @Override
        public Observation getCurrentObservation() {
            return Observation.NOOP;
        }

        @Override
        public void close() {
        }

        @Override
        public void reset() {
        }

        @Override
        public void makeCurrent() {

        }

    }

}
