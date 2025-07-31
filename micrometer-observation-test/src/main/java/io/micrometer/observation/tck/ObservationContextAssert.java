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
package io.micrometer.observation.tck;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationView;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowingConsumer;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.util.Streams.stream;

/**
 * Assertion methods for {@code Observation.Context}s and
 * {@link Observation.ContextView}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link ObservationContextAssert#assertThat(Observation.ContextView)} or
 * {@link ObservationContextAssert#then(Observation.ContextView)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
@SuppressWarnings({ "unchecked", "rawtypes", "UnusedReturnValue" })
public class ObservationContextAssert<SELF extends ObservationContextAssert<SELF>>
        extends AbstractAssert<SELF, Observation.ContextView> {

    protected ObservationContextAssert(Observation.ContextView actual) {
        super(actual, ObservationContextAssert.class);
    }

    /**
     * Creates the assert object for {@link Observation.ContextView}.
     * @param actual context to assert against
     * @return Observation assertions
     */
    public static ObservationContextAssert<?> assertThat(Observation.ContextView actual) {
        return new ObservationContextAssert<>(actual);
    }

    /**
     * Creates the assert object for {@link Observation.ContextView}.
     * @param actual context to assert against
     * @return Observation assertions
     */
    public static ObservationContextAssert<?> then(Observation.ContextView actual) {
        return new ObservationContextAssert<>(actual);
    }

    public SELF hasNameEqualTo(String name) {
        isNotNull();
        String actualName = this.actual.getName();
        if (!Objects.equals(name, actualName)) {
            failWithActualExpectedAndMessage(actualName, name,
                    "Observation should have name equal to <%s> but has <%s>", name, actualName);
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
            failWithActualExpectedAndMessage(actualName, name,
                    "Observation should have name equal to ignoring case <%s> but has <%s>", name, actualName);
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
            failWithActualExpectedAndMessage(actualName, name,
                    "Observation should have contextual name equal to <%s> but has <%s>", name, actualName);
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
            failWithActualExpectedAndMessage(actualName, name,
                    "Observation should have contextual name equal to ignoring case <%s> but has <%s>", name,
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

    public SELF hasKeyValuesCount(int size) {
        isNotNull();
        long actualSize = this.actual.getAllKeyValues().stream().count();
        if (actualSize != size) {
            failWithActualExpectedAndMessage(actualSize, size, "Observation expected to have <%s> keys but has <%s>.",
                    size, actualSize);
        }
        return (SELF) this;
    }

    private List<String> allKeys() {
        List<String> result = lowCardinalityKeys();
        result.addAll(highCardinalityKeys());
        return result;
    }

    public SELF hasOnlyKeys(String... keys) {
        isNotNull();
        Set<String> actualKeys = new LinkedHashSet<>(allKeys());
        List<String> expectedKeys = Arrays.asList(keys);
        boolean sameContent = actualKeys.containsAll(expectedKeys) && actualKeys.size() == expectedKeys.size();

        if (!sameContent) {
            Set<String> extraKeys = new LinkedHashSet<>(actualKeys);
            extraKeys.removeAll(expectedKeys);

            Set<String> missingKeys = new LinkedHashSet<>(expectedKeys);
            missingKeys.removeAll(actualKeys);

            if (!extraKeys.isEmpty() && !missingKeys.isEmpty()) {
                failWithMessage("Observation has unexpected keys %s and misses expected keys %s.", extraKeys,
                        missingKeys);
            }
            else if (!extraKeys.isEmpty()) {
                failWithMessage("Observation has unexpected keys %s.", extraKeys);
            }
            else {
                failWithMessage("Observation is missing expected keys %s.", missingKeys);
            }
        }
        return (SELF) this;
    }

    /**
     * Verifies that the Observation key-value keys are a subset of the given set of keys.
     */
    public SELF hasSubsetOfKeys(String... keys) {
        isNotNull();
        Set<String> actualKeys = new LinkedHashSet<>(allKeys());
        Set<String> expectedKeys = new LinkedHashSet<>(Arrays.asList(keys));

        List<String> extra = stream(actualKeys).filter(actualElement -> !expectedKeys.contains(actualElement))
            .collect(toList());

        if (extra.size() > 0) {
            failWithMessage("Observation keys are not a subset of %s. Found extra keys: %s", expectedKeys, extra);
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
            if (this.actual.getHighCardinalityKeyValue(key) != null) {
                failWithMessage(
                        "Observation should have a low cardinality tag with key <%s> but it was in the wrong (high) cardinality keys. List of all low cardinality keys <%s>",
                        key, lowCardinalityKeys());
            }
            failWithMessage(
                    "Observation should have a low cardinality tag with key <%s> but it's not there. List of all keys <%s>",
                    key, lowCardinalityKeys());
        }
        return (SELF) this;
    }

    public SELF hasLowCardinalityKeyValue(String key, String value) {
        isNotNull();
        hasLowCardinalityKeyValueWithKey(key);
        String tagValue = this.actual.getLowCardinalityKeyValues()
            .stream()
            .filter(tag -> tag.getKey().equals(key))
            .findFirst()
            .get()
            .getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithActualExpectedAndMessage(tagValue, value,
                    "Observation should have a low cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>",
                    key, value, tagValue);
        }
        return (SELF) this;
    }

    /**
     * Return whether it has the given low cardinality key value.
     * @param keyValue key value
     * @return whether it has the given low cardinality key value
     * @since 1.12.0
     */
    public SELF hasLowCardinalityKeyValue(KeyValue keyValue) {
        return hasLowCardinalityKeyValue(keyValue.getKey(), keyValue.getValue());
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
        Optional<KeyValue> optional = this.actual.getLowCardinalityKeyValues()
            .stream()
            .filter(tag -> tag.getKey().equals(key))
            .findFirst();
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

    /**
     * Return whether it does not have the given low cardinality key value.
     * @param keyValue key value
     * @return whether it does not have the given low cardinality key value
     * @since 1.12.0
     */
    public SELF doesNotHaveLowCardinalityKeyValue(KeyValue keyValue) {
        return doesNotHaveLowCardinalityKeyValue(keyValue.getKey(), keyValue.getValue());
    }

    public SELF hasHighCardinalityKeyValueWithKey(String key) {
        isNotNull();
        if (this.actual.getHighCardinalityKeyValues().stream().noneMatch(tag -> tag.getKey().equals(key))) {
            if (this.actual.getLowCardinalityKeyValue(key) != null) {
                failWithMessage(
                        "Observation should have a high cardinality tag with key <%s> but it was in the wrong (low) cardinality keys. List of all high cardinality keys <%s>",
                        key, highCardinalityKeys());
            }
            failWithMessage(
                    "Observation should have a high cardinality tag with key <%s> but it's not there. List of all keys <%s>",
                    key, highCardinalityKeys());
        }
        return (SELF) this;
    }

    public SELF hasHighCardinalityKeyValue(String key, String value) {
        isNotNull();
        hasHighCardinalityKeyValueWithKey(key);
        String tagValue = this.actual.getHighCardinalityKeyValues()
            .stream()
            .filter(tag -> tag.getKey().equals(key))
            .findFirst()
            .get()
            .getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithActualExpectedAndMessage(tagValue, value,
                    "Observation should have a high cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>",
                    key, value, tagValue);
        }
        return (SELF) this;
    }

    /**
     * Return whether it has the given high cardinality key value.
     * @param keyValue key value
     * @return whether it has the given high cardinality key value
     * @since 1.12.0
     */
    public SELF hasHighCardinalityKeyValue(KeyValue keyValue) {
        return hasHighCardinalityKeyValue(keyValue.getKey(), keyValue.getValue());
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
        Optional<KeyValue> optional = this.actual.getHighCardinalityKeyValues()
            .stream()
            .filter(tag -> tag.getKey().equals(key))
            .findFirst();
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

    /**
     * Return whether it does not have the given high cardinality key value.
     * @param keyValue key value
     * @return whether it does not have the given high cardinality key value
     * @since 1.12.0
     */
    public SELF doesNotHaveHighCardinalityKeyValue(KeyValue keyValue) {
        return doesNotHaveHighCardinalityKeyValue(keyValue.getKey(), keyValue.getValue());
    }

    public SELF hasMapEntry(Object key, Object value) {
        isNotNull();
        Object mapValue = this.actual.get(key);
        if (!Objects.equals(mapValue, value)) {
            failWithActualExpectedAndMessage(mapValue, value,
                    "Observation should have an entry for key <%s> with value <%s>. Value was <%s>", key, value,
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
        thenError().withFailMessage("Observation should not have an error, but found <%s>", this.actual.getError())
            .isNull();
        return (SELF) this;
    }

    public SELF hasError() {
        thenError().withFailMessage("Observation should have an error, but none was found").isNotNull();
        return (SELF) this;
    }

    public SELF hasError(Throwable expectedError) {
        hasError();
        thenError()
            .withFailMessage("Observation expected to have error <%s>, but has <%s>", expectedError,
                    this.actual.getError())
            .isEqualTo(expectedError);
        return (SELF) this;
    }

    public ObservationContextAssertReturningThrowableAssert assertThatError() {
        return new ObservationContextAssertReturningThrowableAssert(actual.getError(), this);
    }

    public ObservationContextAssertReturningThrowableAssert thenError() {
        return assertThatError();
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} has a
     * {@link Observation.ContextView#getParentObservation() parent Observation}.
     * @return the instance for further fluent assertion
     */
    public SELF hasParentObservation() {
        isNotNull();
        if (this.actual.getParentObservation() == null) {
            failWithMessage("Observation should have a parent");
        }
        return (SELF) this;
    }

    private ObservationView checkedParentObservation() {
        isNotNull();
        ObservationView p = this.actual.getParentObservation();
        if (p == null) {
            failWithMessage("Observation should have a parent");
        }
        return p;
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} has a
     * {@link Observation.ContextView#getParentObservation() parent Observation} equal to
     * the provided {@link Observation}.
     * @return the instance for further fluent assertion
     */
    public SELF hasParentObservationEqualTo(Observation expectedParent) {
        isNotNull();
        ObservationView realParent = this.actual.getParentObservation();
        if (realParent == null) {
            failWithMessage("Observation should have parent <%s> but has none", expectedParent);
        }
        if (!realParent.equals(expectedParent)) {
            failWithActualExpectedAndMessage(realParent, expectedParent,
                    "Observation should have parent <%s> but has <%s>", expectedParent, realParent);
        }
        return (SELF) this;
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} does not have a
     * {@link Observation.ContextView#getParentObservation() parent Observation}.
     * @return the instance for further fluent assertion
     */
    public SELF doesNotHaveParentObservation() {
        isNotNull();
        if (this.actual.getParentObservation() != null) {
            failWithMessage("Observation should not have a parent but has <%s>", this.actual.getParentObservation());
        }
        return (SELF) this;
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} has a
     * {@link Observation.ContextView#getParentObservation() parent Observation} and that
     * it satisfies assertions performed in the provided
     * {@link java.util.function.Consumer}.
     * @return the instance for further fluent assertion
     */
    public SELF hasParentObservationContextSatisfying(
            ThrowingConsumer<Observation.ContextView> parentContextViewAssertion) {
        ObservationView p = checkedParentObservation();
        try {
            parentContextViewAssertion.accept(p.getContextView());
        }
        catch (Throwable e) {
            failWithMessage("Parent observation does not satisfy given assertion: " + e.getMessage());
        }
        return (SELF) this;
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} has a
     * {@link Observation.ContextView#getParentObservation() parent Observation} and that
     * it matches the provided unnamed predicate.
     *
     * @see #hasParentObservationContextMatching(Predicate, String)
     * @return the instance for further fluent assertion
     */
    public SELF hasParentObservationContextMatching(
            Predicate<? super Observation.ContextView> parentContextViewPredicate) {
        ObservationView p = checkedParentObservation();
        if (!parentContextViewPredicate.test(p.getContextView())) {
            failWithMessage("Observation should have parent that matches given predicate but <%s> didn't", p);
        }
        return (SELF) this;
    }

    /**
     * Verify that the Observation {@link Observation.ContextView} has a
     * {@link Observation.ContextView#getParentObservation() parent Observation} and that
     * it matches the provided named predicate.
     * @return the instance for further fluent assertion
     */
    public SELF hasParentObservationContextMatching(
            Predicate<? super Observation.ContextView> parentContextViewPredicate, String description) {
        ObservationView p = checkedParentObservation();
        if (!parentContextViewPredicate.test(p.getContextView())) {
            failWithMessage("Observation should have parent that matches '%s' predicate but <%s> didn't", description,
                    p);
        }
        return (SELF) this;
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
