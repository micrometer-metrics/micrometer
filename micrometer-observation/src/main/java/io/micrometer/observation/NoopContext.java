/*
 * Copyright 2026 VMware, Inc.
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
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A special {@link Observation.Context} that should be used only in special cases where
 * Observations are disabled/noop. It is an immutable implementation, it will not modify
 * the internals of the context.
 *
 * @author Jonatan Ivanov
 */
class NoopContext extends Observation.Context {

    static final NoopContext INSTANCE = new NoopContext();

    private NoopContext() {
    }

    @Override
    public void setName(String name) {
    }

    @Override
    public void setContextualName(@Nullable String contextualName) {
    }

    @Override
    public void setParentObservation(@Nullable ObservationView parentObservation) {
    }

    @Override
    void setParentFromCurrentObservation(ObservationRegistry registry) {
    }

    @Override
    public void setError(Throwable error) {
    }

    @Override
    public <T> Observation.Context put(Object key, T object) {
        return this;
    }

    @Override
    public @Nullable Object remove(Object key) {
        return null;
    }

    @Override
    public <T> T computeIfAbsent(Object key, Function<Object, ? extends T> mappingFunction) {
        return mappingFunction.apply(key);
    }

    @Override
    public void clear() {
    }

    @Override
    public Observation.Context addLowCardinalityKeyValue(KeyValue keyValue) {
        return this;
    }

    @Override
    public Observation.Context addHighCardinalityKeyValue(KeyValue keyValue) {
        return this;
    }

    @Override
    public Observation.Context removeLowCardinalityKeyValue(String keyName) {
        return this;
    }

    @Override
    public Observation.Context removeHighCardinalityKeyValue(String keyName) {
        return this;
    }

    @Override
    public Observation.Context addLowCardinalityKeyValues(KeyValues keyValues) {
        return this;
    }

    @Override
    public Observation.Context addHighCardinalityKeyValues(KeyValues keyValues) {
        return this;
    }

    @Override
    public Observation.Context removeLowCardinalityKeyValues(String... keyNames) {
        return this;
    }

    @Override
    public Observation.Context removeHighCardinalityKeyValues(String... keyNames) {
        return this;
    }

}
