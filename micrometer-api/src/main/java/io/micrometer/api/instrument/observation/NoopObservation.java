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

import io.micrometer.api.instrument.Tag;

/**
 * No-op implementation of {@link Observation} so that we can disable the instrumentation logic.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 *
 * @since 2.0.0
 */
class NoopObservation implements Observation {
    public static final NoopObservation INSTANCE = new NoopObservation();

    private NoopObservation() {
    }

    @Override
    public Observation contextualName(String contextualName) {
        return this;
    }

    @Override
    public Observation lowCardinalityTag(Tag tag) {
        return this;
    }

    @Override
    public Observation lowCardinalityTag(String key, String value) {
        return this;
    }

    @Override
    public Observation highCardinalityTag(Tag tag) {
        return this;
    }

    @Override
    public Observation highCardinalityTag(String key, String value) {
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

    static class NoOpScope implements Scope {
        private static final Scope INSTANCE = new NoOpScope();

        @Override
        public Observation getCurrentObservation() {
            return NoopObservation.INSTANCE;
        }

        @Override
        public void close() {
        }
    }
}
