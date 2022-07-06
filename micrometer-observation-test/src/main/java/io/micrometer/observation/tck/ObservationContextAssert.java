/*
 * Copyright 2021 VMware, Inc.
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
import io.micrometer.observation.Observation;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Assertion methods for {@code Observation.Context}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link ObservationContextAssert#assertThat(Observation.Context)} or
 * {@link ObservationContextAssert#then(Observation.Context)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings({ "unchecked", "rawtypes", "UnusedReturnValue" })
public class ObservationContextAssert<SELF extends ObservationContextAssert<SELF>>
        extends AbstractAssert<SELF, Observation.Context> {

    protected ObservationContextAssert(Observation.Context actual) {
        super(actual, ObservationContextAssert.class);
    }

    /**
     * Creates the assert object for {@link Observation.Context}.
     * @param actual context to assert against
     * @return Observation assertions
     */
    public static ObservationContextAssert assertThat(Observation.Context actual) {
        return new ObservationContextAssert(actual);
    }

    /**
     * Creates the assert object for {@link Observation.Context}.
     * @param actual context to assert against
     * @return Observation assertions
     */
    public static ObservationContextAssert then(Observation.Context actual) {
        return new ObservationContextAssert(actual);
    }

    public SELF hasNameEqualTo(String name) {
        isNotNull();
        String actualName = this.actual.getName();
        if (!Objects.equals(name, actualName)) {
            failWithMessage("Observation should have name equal to <%s> but has <%s>", name, actualName);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveNameEqualTo(String name) {
        isNotNull();
        String actualName = this.actual.getName();
        if (Objects.equals(name, actualName)) {
            failWithMessage("Observation should not have name equal to <%s>", actualName);
        }
        return (SELF) this;
    }

    public SELF hasNameEqualToIgnoringCase(String name) {
        isNotNull();
        String actualName = this.actual.getName();
        if (!name.equalsIgnoreCase(actualName)) {
            failWithMessage("Observation should have name equal to ignoring case <%s> but has <%s>", name, actualName);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveNameEqualToIgnoringCase(String name) {
        isNotNull();
        String actualName = this.actual.getName();
        if (name.equalsIgnoreCase(actualName)) {
            failWithMessage("Observation should not have name equal to ignoring case <%s>", actualName);
        }
        return (SELF) this;
    }

    public SELF hasContextualNameEqualTo(String name) {
        isNotNull();
        String actualName = this.actual.getContextualName();
        if (!Objects.equals(name, actualName)) {
            failWithMessage("Observation should have contextual name equal to <%s> but has <%s>", name, actualName);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveContextualNameEqualTo(String name) {
        isNotNull();
        String actualName = this.actual.getContextualName();
        if (name.equals(actualName)) {
            failWithMessage("Observation should not have contextual name equal to <%s>", actualName);
        }
        return (SELF) this;
    }

    public SELF hasContextualNameEqualToIgnoringCase(String name) {
        isNotNull();
        String actualName = this.actual.getContextualName();
        if (!name.equalsIgnoreCase(actualName)) {
            failWithMessage("Observation should have contextual name equal to ignoring case <%s> but has <%s>", name,
                    actualName);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveContextualNameEqualToIgnoringCase(String name) {
        isNotNull();
        String actualName = this.actual.getContextualName();
        if (name.equalsIgnoreCase(actualName)) {
            failWithMessage("Observation should not have contextual name equal to ignoring case <%s>", actualName);
        }
        return (SELF) this;
    }

    public SELF hasNoKeyValues() {
        isNotNull();
        KeyValues tags = this.actual.getAllKeyValues();
        if (tags.stream().findAny().isPresent()) {
            failWithMessage("Observation should have no tags but has <%s>", tags);
        }
        return (SELF) this;
    }

    public SELF hasAnyKeyValues() {
        isNotNull();
        KeyValues tags = this.actual.getAllKeyValues();
        if (!tags.stream().findAny().isPresent()) {
            failWithMessage("Observation should have any tags but has none");
        }
        return (SELF) this;
    }

    private List<String> lowCardinalityKeys() {
        return this.actual.getLowCardinalityKeyValues().stream().map(KeyValue::getKey).collect(Collectors.toList());
    }

    private List<String> highCardinalityKeys() {
        return this.actual.getHighCardinalityKeyValues().stream().map(KeyValue::getKey).collect(Collectors.toList());
    }

    public SELF hasLowCardinalityKeyValueWithKey(String key) {
        isNotNull();
        if (this.actual.getLowCardinalityKeyValues().stream().noneMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage(
                    "Observation should have a low cardinality tag with key <%s> but it's not there. List of all keys <%s>",
                    key, lowCardinalityKeys());
        }
        return (SELF) this;
    }

    public SELF hasLowCardinalityKeyValue(String key, String value) {
        isNotNull();
        hasLowCardinalityKeyValueWithKey(key);
        String tagValue = this.actual.getLowCardinalityKeyValues().stream().filter(tag -> tag.getKey().equals(key))
                .findFirst().get().getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithMessage(
                    "Observation should have a low cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>",
                    key, value, tagValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveLowCardinalityKeyValueWithKey(String key) {
        isNotNull();
        if (this.actual.getLowCardinalityKeyValues().stream().anyMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should not have a low cardinality tag with key <%s>", key);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveLowCardinalityKeyValue(String key, String value) {
        isNotNull();
        doesNotHaveLowCardinalityKeyValueWithKey(key);
        Optional<KeyValue> optional = this.actual.getLowCardinalityKeyValues().stream()
                .filter(tag -> tag.getKey().equals(key)).findFirst();
        if (!optional.isPresent()) {
            return (SELF) this;
        }
        String tagValue = optional.get().getValue();
        if (Objects.equals(tagValue, value)) {
            failWithMessage("Observation should not have a low cardinality tag with key <%s> and value <%s>", key,
                    value);
        }
        return (SELF) this;
    }

    public SELF hasHighCardinalityKeyValueWithKey(String key) {
        isNotNull();
        if (this.actual.getHighCardinalityKeyValues().stream().noneMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage(
                    "Observation should have a high cardinality tag with key <%s> but it's not there. List of all keys <%s>",
                    key, highCardinalityKeys());
        }
        return (SELF) this;
    }

    public SELF hasHighCardinalityKeyValue(String key, String value) {
        isNotNull();
        hasHighCardinalityKeyValueWithKey(key);
        String tagValue = this.actual.getHighCardinalityKeyValues().stream().filter(tag -> tag.getKey().equals(key))
                .findFirst().get().getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithMessage(
                    "Observation should have a high cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>",
                    key, value, tagValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveHighCardinalityKeyValueWithKey(String key) {
        isNotNull();
        if (this.actual.getHighCardinalityKeyValues().stream().anyMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should not have a high cardinality tag with key <%s>", key);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveHighCardinalityKeyValue(String key, String value) {
        isNotNull();
        doesNotHaveHighCardinalityKeyValueWithKey(key);
        Optional<KeyValue> optional = this.actual.getHighCardinalityKeyValues().stream()
                .filter(tag -> tag.getKey().equals(key)).findFirst();
        if (!optional.isPresent()) {
            return (SELF) this;
        }
        String tagValue = optional.get().getValue();
        if (tagValue.equals(value)) {
            failWithMessage("Observation should not have a high cardinality tag with key <%s> and value <%s>", key,
                    value);
        }
        return (SELF) this;
    }

    public SELF hasMapEntry(Object key, Object value) {
        isNotNull();
        Object mapValue = this.actual.get(key);
        if (!Objects.equals(mapValue, value)) {
            failWithMessage("Observation should have an entry for key <%s> with value <%s>. Value was <%s>", key, value,
                    mapValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveMapEntry(Object key, Object value) {
        isNotNull();
        Object mapValue = this.actual.get(key);
        if (Objects.equals(mapValue, value)) {
            failWithMessage("Observation should not have an entry for key <%s> with value <%s>", key, value, mapValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveError() {
        isNotNull();
        Optional<Throwable> error = this.actual.getError();
        error.ifPresent(throwable -> failWithMessage("Observation should not have an error, found <%s>", throwable));
        return (SELF) this;
    }

    public SELF hasError() {
        isNotNull();
        Optional<Throwable> error = this.actual.getError();
        if (!error.isPresent()) {
            failWithMessage("Observation should have an error, but none was found");
        }
        return (SELF) this;
    }

    public SELF hasError(Throwable expectedError) {
        isNotNull();
        hasError();
        Throwable error = this.actual.getError().get();
        if (!error.equals(expectedError)) {
            failWithMessage("Observation expected to have error <%s>, but has <%s>", expectedError, error);
        }
        return (SELF) this;
    }

    public ObservationContextAssertReturningThrowableAssert assertThatError() {
        return new ObservationContextAssertReturningThrowableAssert(actual.getError().orElse(null), this);
    }

    public ObservationContextAssertReturningThrowableAssert thenError() {
        return assertThatError();
    }

    public static class ObservationContextAssertReturningThrowableAssert
            extends AbstractThrowableAssert<ObservationContextAssertReturningThrowableAssert, Throwable> {

        private final ObservationContextAssert observationContextAssert;

        public ObservationContextAssertReturningThrowableAssert(Throwable throwable,
                ObservationContextAssert observationContextAssert) {
            super(throwable, ObservationContextAssertReturningThrowableAssert.class);
            this.observationContextAssert = observationContextAssert;
        }

        public ObservationContextAssert backToContext() {
            return this.observationContextAssert;
        }

    }

}
