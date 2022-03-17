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
package io.micrometer.core.tck;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.micrometer.observation.Observation;
import io.micrometer.observation.Tag;
import io.micrometer.observation.Tags;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractThrowableAssert;

/**
 * Assertion methods for {@code Observation.Context}s.
 * <p>
 * To create a new instance of this class, invoke {@link ObservationContextAssert#assertThat(Observation.Context)}
 * or {@link ObservationContextAssert#then(Observation.Context)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
public class ObservationContextAssert<SELF extends ObservationContextAssert<SELF>> extends AbstractAssert<SELF, Observation.Context> {

    protected ObservationContextAssert(Observation.Context actual) {
        super(actual, ObservationContextAssert.class);
    }

    /**
     * Creates the assert object for {@link Observation.Context}.
     *
     * @param actual context to assert against
     * @return Observation assertions
     */
    public static ObservationContextAssert assertThat(Observation.Context actual) {
        return new ObservationContextAssert(actual);
    }

    /**
     * Creates the assert object for {@link Observation.Context}.
     *
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
            failWithMessage("Observation should have name equal to but <%s> but has <%s>", name, actualName);
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
            failWithMessage("Observation should have name equal to ignoring case but <%s> but has <%s>", name, actualName);
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
            failWithMessage("Observation should have contextual name equal to but <%s> but has <%s>", name, actualName);
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
            failWithMessage("Observation should have contextual name equal to ignoring case but <%s> but has <%s>", name, actualName);
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

    public SELF hasNoTags() {
        isNotNull();
        Tags tags = this.actual.getAllTags();
        if (tags.stream().findAny().isPresent()) {
            failWithMessage("Observation should have no tags but has <%s>", tags);
        }
        return (SELF) this;
    }

    public SELF hasAnyTags() {
        isNotNull();
        Tags tags = this.actual.getAllTags();
        if (!tags.stream().findAny().isPresent()) {
            failWithMessage("Observation should have any tags but has none");
        }
        return (SELF) this;
    }

    private List<String> lowCardinalityTagKeys() {
        return this.actual.getLowCardinalityTags().stream().map(Tag::getKey).collect(Collectors.toList());
    }

    private List<String> highCardinalityTagKeys() {
        return this.actual.getHighCardinalityTags().stream().map(Tag::getKey).collect(Collectors.toList());
    }

    public SELF hasLowCardinalityTagWithKey(String key) {
        isNotNull();
        if (this.actual.getLowCardinalityTags().stream().noneMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should have a low cardinality tag with key <%s> but it's not there. List of all keys <%s>", key, lowCardinalityTagKeys());
        }
        return (SELF) this;
    }

    public SELF hasLowCardinalityTag(String key, String value) {
        isNotNull();
        hasLowCardinalityTagWithKey(key);
        String tagValue = this.actual.getLowCardinalityTags().stream().filter(tag -> tag.getKey().equals(key)).findFirst().get().getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithMessage("Observation should have a low cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>", key, value, tagValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveLowCardinalityTagWithKey(String key) {
        isNotNull();
        if (this.actual.getLowCardinalityTags().stream().anyMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should not have a low cardinality tag with key <%s>", key);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveLowCardinalityTag(String key, String value) {
        isNotNull();
        doesNotHaveLowCardinalityTagWithKey(key);
        Optional<Tag> optional = this.actual.getLowCardinalityTags().stream().filter(tag -> tag.getKey().equals(key)).findFirst();
        if (!optional.isPresent()) {
            return (SELF) this;
        }
        String tagValue = optional.get().getValue();
        if (Objects.equals(tagValue, value)) {
            failWithMessage("Observation should not have a low cardinality tag with key <%s> and value <%s>", key, value);
        }
        return (SELF) this;
    }


    public SELF hasHighCardinalityTagWithKey(String key) {
        isNotNull();
        if (this.actual.getHighCardinalityTags().stream().noneMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should have a high cardinality tag with key <%s> but it's not there. List of all keys <%s>", key, highCardinalityTagKeys());
        }
        return (SELF) this;
    }

    public SELF hasHighCardinalityTag(String key, String value) {
        isNotNull();
        hasHighCardinalityTagWithKey(key);
        String tagValue = this.actual.getHighCardinalityTags().stream().filter(tag -> tag.getKey().equals(key)).findFirst().get().getValue();
        if (!Objects.equals(tagValue, value)) {
            failWithMessage("Observation should have a high cardinality tag with key <%s> and value <%s>. The key is correct but the value is <%s>", key, value, tagValue);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveHighCardinalityTagWithKey(String key) {
        isNotNull();
        if (this.actual.getHighCardinalityTags().stream().anyMatch(tag -> tag.getKey().equals(key))) {
            failWithMessage("Observation should not have a high cardinality tag with key <%s>", key);
        }
        return (SELF) this;
    }

    public SELF doesNotHaveHighCardinalityTag(String key, String value) {
        isNotNull();
        doesNotHaveHighCardinalityTagWithKey(key);
        Optional<Tag> optional = this.actual.getHighCardinalityTags().stream().filter(tag -> tag.getKey().equals(key)).findFirst();
        if (!optional.isPresent()) {
            return (SELF) this;
        }
        String tagValue = optional.get().getValue();
        if (tagValue.equals(value)) {
            failWithMessage("Observation should not have a high cardinality tag with key <%s> and value <%s>", key, value);
        }
        return (SELF) this;
    }

    public SELF hasMapEntry(Object key, Object value) {
        isNotNull();
        Object mapValue = this.actual.get(key);
        if (!Objects.equals(mapValue, value)) {
            failWithMessage("Observation should have an entry for key <%s> with value <%s>. Value was <%s>", key, value, mapValue);
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

    public ObservationContextAssertReturningThrowableAssert assertThatThrowable() {
        return new ObservationContextAssertReturningThrowableAssert(actual.getError().orElse(null), this);
    }

    public ObservationContextAssertReturningThrowableAssert thenThrowable() {
        return assertThatThrowable();
    }

    public static class ObservationContextAssertReturningThrowableAssert extends AbstractThrowableAssert<ObservationContextAssertReturningThrowableAssert, Throwable> {

        private final ObservationContextAssert observationContextAssert;

        public ObservationContextAssertReturningThrowableAssert(Throwable throwable, ObservationContextAssert observationContextAssert) {
            super(throwable, ObservationContextAssertReturningThrowableAssert.class);
            this.observationContextAssert = observationContextAssert;
        }

        public ObservationContextAssert backToContext() {
            return this.observationContextAssert;
        }
    }
}
