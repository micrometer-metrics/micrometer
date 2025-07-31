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
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.function.Supplier;

import static io.micrometer.observation.tck.ObservationContextAssert.assertThat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenNoException;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ObservationContextAssertTests {

    ObservationRegistry registry;

    TestContext context;

    static class TestContext extends Observation.Context implements Supplier<TestContext> {

        @Override
        public TestContext get() {
            return this;
        }

    }

    @BeforeEach
    void beforeEach() {
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(c -> true);
        context = new TestContext();
    }

    @Test
    void should_not_throw_exception_when_name_correct() {
        context.setName("foo");

        thenNoException().isThrownBy(() -> assertThat(context).hasNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_name_incorrect() {
        context.setName("foo");

        thenThrownBy(() -> assertThat(context).hasNameEqualTo("bar")).isInstanceOfSatisfying(AssertionFailedError.class,
                error -> {
                    then(error.getActual().getStringRepresentation()).isEqualTo("foo");
                    then(error.getExpected().getStringRepresentation()).isEqualTo("bar");
                });
    }

    @Test
    void should_not_throw_exception_when_name_incorrect() {
        context.setName("bar");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_name_correct() {
        context.setName("foo");

        thenThrownBy(() -> assertThat(context).doesNotHaveNameEqualTo("foo")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_contextual_name_correct() {
        context.setContextualName("foo");

        thenNoException().isThrownBy(() -> assertThat(context).hasContextualNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_contextual_name_incorrect() {
        context.setContextualName("foo");

        thenThrownBy(() -> assertThat(context).hasContextualNameEqualTo("bar"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("foo");
                then(error.getExpected().getStringRepresentation()).isEqualTo("bar");
            });
    }

    @Test
    void should_not_throw_exception_when_contextual_name_incorrect() {
        context.setContextualName("bar");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveContextualNameEqualTo("foo"));
    }

    @Test
    void should_throw_exception_when_contextual_name_correct() {
        context.setContextualName("foo");

        thenThrownBy(() -> assertThat(context).doesNotHaveContextualNameEqualTo("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_name_ignore_case_correct() {
        context.setName("FOO");

        thenNoException().isThrownBy(() -> assertThat(context).hasNameEqualToIgnoringCase("foo"));
    }

    @Test
    void should_throw_exception_when_name_ignore_case_incorrect() {
        context.setName("foo");

        thenThrownBy(() -> assertThat(context).hasNameEqualToIgnoringCase("bar"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("foo");
                then(error.getExpected().getStringRepresentation()).isEqualTo("bar");
            });
    }

    @Test
    void should_not_throw_exception_when_name_ignore_case_incorrect() {
        context.setName("bar");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveNameEqualToIgnoringCase("foo"));
    }

    @Test
    void should_throw_exception_when_name_ignore_case_correct() {
        context.setName("BAR");

        thenThrownBy(() -> assertThat(context).doesNotHaveNameEqualToIgnoringCase("bar"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_contextual_name_ignore_case_correct() {
        context.setContextualName("FOO");

        thenNoException().isThrownBy(() -> assertThat(context).hasContextualNameEqualToIgnoringCase("foo"));
    }

    @Test
    void should_throw_exception_when_contextual_name_ignore_case_incorrect() {
        context.setContextualName("foo");

        thenThrownBy(() -> assertThat(context).hasContextualNameEqualToIgnoringCase("bar"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("foo");
                then(error.getExpected().getStringRepresentation()).isEqualTo("bar");
            });
    }

    @Test
    void should_not_throw_exception_when_contextual_name_ignore_case_incorrect() {
        context.setContextualName("bar");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveContextualNameEqualToIgnoringCase("foo"));
    }

    @Test
    void should_throw_exception_when_contextual_name_ignore_case_correct() {
        context.setContextualName("BAR");

        thenThrownBy(() -> assertThat(context).doesNotHaveContextualNameEqualToIgnoringCase("bar"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_key_count_matches() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("low", "foo");
        observation.highCardinalityKeyValue("high", "bar");

        thenNoException().isThrownBy(() -> assertThat(context).hasKeyValuesCount(2));
    }

    @Test
    void should_throw_exception_when_key_count_differs() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("low", "foo");
        observation.highCardinalityKeyValue("high", "bar");

        thenThrownBy(() -> assertThat(context).hasKeyValuesCount(1)).isInstanceOf(AssertionError.class)
            .hasMessage("Observation expected to have <1> keys but has <2>.")
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("2");
                then(error.getExpected().getStringRepresentation()).isEqualTo("1");
            });

        thenThrownBy(() -> assertThat(context).hasKeyValuesCount(3)).isInstanceOf(AssertionError.class)
            .hasMessage("Observation expected to have <3> keys but has <2>.")
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("2");
                then(error.getExpected().getStringRepresentation()).isEqualTo("3");
            });
    }

    @Test
    void should_not_throw_exception_when_keys_match() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("low", "foo");
        observation.highCardinalityKeyValue("high", "bar");

        thenNoException().isThrownBy(() -> ObservationContextAssert.assertThat(context).hasOnlyKeys("low", "high"));
    }

    @Test
    void should_throw_exception_when_keys_missing() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("found", "foo");

        thenThrownBy(() -> ObservationContextAssert.assertThat(context).hasOnlyKeys("found", "low", "high"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("Observation is missing expected keys [low, high].");
    }

    @Test
    void should_throw_exception_when_keys_extras() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("found", "foo");
        observation.lowCardinalityKeyValue("low", "foo");
        observation.highCardinalityKeyValue("high", "foo");

        thenThrownBy(() -> ObservationContextAssert.assertThat(context).hasOnlyKeys("found"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("Observation has unexpected keys [low, high].");
    }

    @Test
    void should_throw_exception_when_keys_both_missing_and_extras() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("found", "foo");
        observation.lowCardinalityKeyValue("low", "foo");
        observation.highCardinalityKeyValue("high", "foo");

        thenThrownBy(() -> ObservationContextAssert.assertThat(context).hasOnlyKeys("notfound", "found"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("Observation has unexpected keys [low, high] and misses expected keys [notfound].");
    }

    @Test
    void should_not_throw_exception_when_low_cardinality_key_value_exists() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(context).hasLowCardinalityKeyValue("foo", "bar")
            .hasLowCardinalityKeyValue(KeyValue.of("foo", "bar")));
    }

    @Test
    void should_throw_exception_when_low_cardinality_key_value_missing() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("foo", "bar");

        thenThrownBy(() -> assertThat(context).hasLowCardinalityKeyValue("foo", "baz"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("bar");
                then(error.getExpected().getStringRepresentation()).isEqualTo("baz");
            });
        thenThrownBy(() -> assertThat(context).hasLowCardinalityKeyValueWithKey("bar"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_low_cardinality_key_value_missing_but_in_high_cardinality_keys() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("low", "l");
        observation.highCardinalityKeyValue("high", "h");

        thenThrownBy(() -> assertThat(context).hasLowCardinalityKeyValue("high", "h"))
            .isInstanceOf(AssertionError.class)
            .hasMessage(
                    "Observation should have a low cardinality tag with key <high> but it was in the wrong (high) cardinality keys. List of all low cardinality keys <[low]>");
        thenThrownBy(() -> assertThat(context).hasLowCardinalityKeyValueWithKey("high"))
            .isInstanceOf(AssertionError.class)
            .hasMessage(
                    "Observation should have a low cardinality tag with key <high> but it was in the wrong (high) cardinality keys. List of all low cardinality keys <[low]>");
    }

    @Test
    void should_not_throw_exception_when_high_cardinality_key_value_exists() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(context).hasHighCardinalityKeyValue("foo", "bar")
            .hasHighCardinalityKeyValue(KeyValue.of("foo", "bar")));
    }

    @Test
    void should_throw_exception_when_high_cardinality_key_value_missing() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "bar");

        thenThrownBy(() -> assertThat(context).hasHighCardinalityKeyValue("foo", "baz"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("bar");
                then(error.getExpected().getStringRepresentation()).isEqualTo("baz");
            });
        thenThrownBy(() -> assertThat(context).hasHighCardinalityKeyValueWithKey("bar"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_high_cardinality_key_value_missing_but_in_low_cardinality_keys() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("low", "l");
        observation.highCardinalityKeyValue("high", "h");

        thenThrownBy(() -> assertThat(context).hasHighCardinalityKeyValue("low", "l"))
            .isInstanceOf(AssertionError.class)
            .hasMessage(
                    "Observation should have a high cardinality tag with key <low> but it was in the wrong (low) cardinality keys. List of all high cardinality keys <[high]>");
        thenThrownBy(() -> assertThat(context).hasHighCardinalityKeyValueWithKey("low"))
            .isInstanceOf(AssertionError.class)
            .hasMessage(
                    "Observation should have a high cardinality tag with key <low> but it was in the wrong (low) cardinality keys. List of all high cardinality keys <[high]>");
    }

    @Test
    void should_not_throw_exception_when_high_cardinality_key_value_present() {
        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveHighCardinalityKeyValue("foo", "bar")
            .doesNotHaveHighCardinalityKeyValue(KeyValue.of("foo", "bar")));
    }

    @Test
    void should_throw_exception_when_high_cardinality_key_value_present() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "bar");

        thenThrownBy(() -> assertThat(context).doesNotHaveHighCardinalityKeyValue("foo", "bar"))
            .isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(context).doesNotHaveHighCardinalityKeyValueWithKey("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_high_cardinality_key_value_present_with_other_value() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "other");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveHighCardinalityKeyValue("foo", "bar"));

        thenThrownBy(() -> assertThat(context).doesNotHaveHighCardinalityKeyValueWithKey("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_low_cardinality_key_value_missing() {
        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveLowCardinalityKeyValue("foo", "bar")
            .doesNotHaveLowCardinalityKeyValue(KeyValue.of("foo", "bar")));
    }

    @Test
    void should_not_throw_exception_when_low_cardinality_key_value_present_with_other_value() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("foo", "other");

        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveLowCardinalityKeyValue("foo", "bar"));

        thenThrownBy(() -> assertThat(context).doesNotHaveLowCardinalityKeyValueWithKey("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_low_cardinality_key_value_present() {
        Observation observation = Observation.start("foo", context, registry);
        observation.lowCardinalityKeyValue("foo", "bar");

        thenThrownBy(() -> assertThat(context).doesNotHaveLowCardinalityKeyValue("foo", "bar"))
            .isInstanceOf(AssertionError.class);
        thenThrownBy(() -> assertThat(context).doesNotHaveLowCardinalityKeyValueWithKey("foo"))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_any_tags_exist() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(context).hasAnyKeyValues());
    }

    @Test
    void should_throw_exception_when_no_tags_present() {
        thenThrownBy(() -> assertThat(context).hasAnyKeyValues()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_no_tags_exist() {
        thenNoException().isThrownBy(() -> assertThat(context).hasNoKeyValues());
    }

    @Test
    void should_throw_exception_when_tags_present() {
        Observation observation = Observation.start("foo", context, registry);
        observation.highCardinalityKeyValue("foo", "bar");

        thenThrownBy(() -> assertThat(context).hasNoKeyValues()).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_throw_exception_when_map_entry_missing() {
        context.put("foo", "bar");

        thenThrownBy(() -> assertThat(context).hasMapEntry("foo", "baz"))
            .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                then(error.getActual().getStringRepresentation()).isEqualTo("bar");
                then(error.getExpected().getStringRepresentation()).isEqualTo("baz");
            });
    }

    @Test
    void should_not_throw_exception_when_map_entry_present() {
        context.put("foo", "bar");

        thenNoException().isThrownBy(() -> assertThat(context).hasMapEntry("foo", "bar"));
    }

    @Test
    void should_throw_exception_when_map_entry_present() {
        context.put("foo", "bar");

        thenThrownBy(() -> assertThat(context).doesNotHaveMapEntry("foo", "bar")).isInstanceOf(AssertionError.class);
    }

    @Test
    void should_not_throw_exception_when_map_entry_missing() {
        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveMapEntry("foo", "bar"));
    }

    @Test
    void should_not_throw_when_does_not_have_error() {
        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveError());
    }

    @Test
    void should_throw_when_unexpected_error() {
        Throwable expected = new IllegalStateException("test");

        Observation observation = Observation.start("foo", context, registry);
        observation.error(expected);

        thenThrownBy(() -> assertThat(context).doesNotHaveError()).hasMessageContaining(
                "Observation should not have an error, but found <java.lang.IllegalStateException: test>");
    }

    @Test
    void should_not_throw_when_has_error() {
        Throwable expected = new IllegalStateException("test");

        Observation observation = Observation.start("foo", context, registry);
        observation.error(expected);

        thenNoException().isThrownBy(() -> assertThat(context).hasError());
    }

    @Test
    void should_throw_when_has_error_missing() {
        thenThrownBy(() -> assertThat(context).hasError())
            .hasMessageContaining("Observation should have an error, but none was found");
    }

    @Test
    void should_not_throw_when_has_specific_error() {
        Throwable expected = new IllegalStateException("test");

        Observation observation = Observation.start("foo", context, registry);
        observation.error(expected);

        thenNoException().isThrownBy(() -> assertThat(context).hasError(expected));
    }

    @Test
    void should_throw_when_has_specific_error_missing() {
        Throwable expected = new IllegalStateException("test");

        thenThrownBy(() -> assertThat(context).hasError(expected))
            .hasMessageContaining("Observation should have an error, but none was found");
    }

    @Test
    void should_throw_when_has_specific_error_does_not_match() {
        Throwable expected = new IllegalStateException("test expected");
        Throwable actual = new IllegalArgumentException("test actual");

        Observation observation = Observation.start("foo", context, registry);
        observation.error(actual);

        thenThrownBy(() -> assertThat(context).hasError(expected))
            .hasMessageContaining("Observation expected to have error <java.lang.IllegalStateException: test expected>,"
                    + " but has <java.lang.IllegalArgumentException: test actual>");
    }

    @Test
    void should_jump_to_and_back_from_throwable_assert() {
        context.setName("foo");
        context.setError(new RuntimeException("bar"));

        thenNoException().isThrownBy(() -> assertThat(context).hasNameEqualTo("foo")
            .thenError()
            .hasMessage("bar")
            .backToContext()
            .hasNameEqualTo("foo"));
    }

    private Observation mockParent() {
        Observation parent = spy(Observation.createNotStarted("parent", registry));
        parent.contextualName("expected");
        when(parent.toString()).thenReturn("PARENT_OBSERVATION");
        return parent;
    }

    private Observation mockNotParent() {
        Observation parent = spy(Observation.createNotStarted("notParent", registry));
        parent.contextualName("notExpected");
        when(parent.toString()).thenReturn("NOT_PARENT_OBSERVATION");
        return parent;
    }

    @Test
    void should_not_throw_when_has_parent_observation() {
        Observation parent = Observation.createNotStarted("parent", registry);
        context.setParentObservation(parent);

        thenNoException().isThrownBy(() -> assertThat(context).hasParentObservation());
    }

    @Test
    void should_throw_when_no_parent_observation() {
        thenThrownBy(() -> assertThat(context).hasParentObservation()).hasMessage("Observation should have a parent");
    }

    @Test
    void should_not_throw_when_no_parent_observation() {
        thenNoException().isThrownBy(() -> assertThat(context).doesNotHaveParentObservation());
    }

    @Test
    void should_throw_when_has_parent_observation() {
        Observation parent = mockParent();
        context.setParentObservation(parent);

        thenThrownBy(() -> assertThat(context).doesNotHaveParentObservation())
            .hasMessage("Observation should not have a parent but has <PARENT_OBSERVATION>");
    }

    @Test
    void should_not_throw_when_parent_observation_equals() {
        Observation parent = Observation.createNotStarted("parent", registry);
        context.setParentObservation(parent);

        thenNoException().isThrownBy(() -> assertThat(context).hasParentObservationEqualTo(parent));
    }

    @Test
    void should_throw_when_parent_observation_not_equals() {
        Observation parent = mockParent();
        context.setParentObservation(parent);
        Observation notParent = mockNotParent();

        thenThrownBy(() -> assertThat(context).hasParentObservationEqualTo(notParent))
            .hasMessage("Observation should have parent <NOT_PARENT_OBSERVATION> but has <PARENT_OBSERVATION>");
    }

    @Test
    void should_throw_when_parent_observation_is_null() {
        Observation notParent = mockNotParent();

        thenThrownBy(() -> assertThat(context).hasParentObservationEqualTo(notParent))
            .hasMessage("Observation should have parent <NOT_PARENT_OBSERVATION> but has none");
    }

    @Test
    void should_not_throw_when_parent_observation_matches() {
        Observation parent = Observation.createNotStarted("parent", registry);
        parent.contextualName("expected");
        context.setParentObservation(parent);

        thenNoException().isThrownBy(() -> assertThat(context)
            .hasParentObservationContextMatching(c -> "expected".equals(c.getContextualName())));
    }

    @Test
    void should_throw_when_parent_observation_not_matches() {
        Observation parent = mockParent();
        context.setParentObservation(parent);

        thenThrownBy(() -> assertThat(context)
            .hasParentObservationContextMatching(c -> "notExpected".equals(c.getContextualName())))
            .hasMessage("Observation should have parent that matches given predicate but <PARENT_OBSERVATION> didn't");
    }

    @Test
    void should_not_throw_when_parent_observation_matches_with_description() {
        Observation parent = Observation.createNotStarted("parent", registry);
        parent.contextualName("expected");
        context.setParentObservation(parent);

        thenNoException().isThrownBy(() -> assertThat(context)
            .hasParentObservationContextMatching(c -> "expected".equals(c.getContextualName()), "withDescription"));
    }

    @Test
    void should_throw_when_parent_observation_not_matches_with_description() {
        Observation parent = mockParent();
        context.setParentObservation(parent);

        thenThrownBy(() -> assertThat(context)
            .hasParentObservationContextMatching(c -> "notExpected".equals(c.getContextualName()), "withDescription"))
            .hasMessage(
                    "Observation should have parent that matches 'withDescription' predicate but <PARENT_OBSERVATION> didn't");
    }

    @Test
    void should_not_throw_when_parent_observation_satisfies() {
        Observation parent = mockParent();
        context.setParentObservation(parent);

        thenNoException().isThrownBy(() -> assertThat(context)
            .hasParentObservationContextSatisfying(c -> assertThat(c).hasContextualNameEqualTo("expected")));
    }

    @Test
    void should_throw_when_parent_observation_not_satisfies() {
        Observation parent = mockParent();
        context.setParentObservation(parent);

        thenThrownBy(() -> assertThat(context)
            .hasParentObservationContextSatisfying(c -> assertThat(c).hasContextualNameEqualTo("notExpected")))
            .hasMessage(
                    "Parent observation does not satisfy given assertion: Observation should have contextual name equal to <notExpected> but has <expected>");

        thenThrownBy(() -> assertThat(context).hasParentObservationContextSatisfying(c -> assertThat(c).hasError()))
            .hasMessage(
                    "Parent observation does not satisfy given assertion: Observation should have an error, but none was found");
    }

    @Test
    void hasSubsetOfKeys() {
        context.addLowCardinalityKeyValues(KeyValues.of("a", "1", "b", "2", "c", "3", "d", "4"));
        thenThrownBy(() -> assertThat(context).hasSubsetOfKeys("a", "b"))
            .hasMessage("Observation keys are not a subset of [a, b]. Found extra keys: [c, d]");
    }

}
