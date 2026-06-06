/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.common;

import io.micrometer.common.docs.KeyName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reproduces the inconsistent null handling across the {@link KeyValue} /
 * {@link KeyValues} factory surface. Each test asserts the <em>current</em> behavior
 * (i.e. they pass on {@code main} today) and exists as a baseline so any future
 * null-handling PR can be evaluated against the actual contract divergence.
 *
 * <p>
 * Summary of findings reproduced here:
 * <ul>
 * <li>{@link KeyValue#of(String, String)} via {@link ImmutableKeyValue} NPEs on null key
 * OR null value.</li>
 * <li>{@link KeyValue#of(String, Object, java.util.function.Predicate)} via
 * {@link ValidatedKeyValue} tolerates null value and stores the literal string
 * {@code "null"} (asymmetric).</li>
 * <li>{@link KeyValues} builders propagate nulls into downstream NPEs at {@code dedup} /
 * {@code merge} rather than failing fast at the entry point.</li>
 * </ul>
 */
class ValidatedKeyValueTest {

    // --- (1) ImmutableKeyValue path: requireNonNull on both args ---

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_nullKey_throwsNpe() {
        assertThatThrownBy(() -> KeyValue.of((String) null, "v")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_nullValue_throwsNpe() {
        assertThatThrownBy(() -> KeyValue.of("k", (String) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_keyNameNull_throwsNpe() {
        // KeyName#asString() is called on null reference → NPE during interface dispatch
        assertThatThrownBy(() -> KeyValue.of((KeyName) null, "v")).isInstanceOf(NullPointerException.class);
    }

    // --- (2) ValidatedKeyValue path: String.valueOf(null) == "null" ---

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_validator_nullValue_returnsLiteralStringNull() {
        // PASSES today — the validator overload uses String.valueOf which produces
        // "null".
        // This is the asymmetry vs. the (1) path above.
        KeyValue kv = KeyValue.of("k", (Object) null, v -> true);
        assertThat(kv.getValue()).isEqualTo("null");
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_keyNameValidator_nullKeyName_throwsNpe() {
        // KeyValue.of(KeyName, T, Predicate) calls keyName.asString() first
        // → NPE on null KeyName, same as (1) above
        assertThatThrownBy(() -> KeyValue.of((KeyName) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
    }

    // --- (3) Extractor-based factory: extracts then constructs ---

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_extractor_nullKey_throwsNpe() {
        Object element = new Object();
        assertThatThrownBy(() -> KeyValue.of(element, e -> null, e -> "v")).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValueOf_extractor_nullValue_throwsNpe() {
        Object element = new Object();
        assertThatThrownBy(() -> KeyValue.of(element, e -> "k", e -> null)).isInstanceOf(NullPointerException.class);
    }

    // --- (4) KeyValues builders: downstream NPEs ---

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_iterableWithNullKeyElement_throwsNpe() {
        // 2+ elements with null keys: toKeyValues → isSortedSet calls
        // compareTo which calls getKey().compareTo(o.getKey()) → NPE.
        // A singleton null-key element silently survives (length-1 short-circuit),
        // which is itself contract confusion — see
        // keyValuesOf_singletonWithNullKeyElement_survives
        // for that case.
        KeyValue bad1 = new NullKeyKeyValue();
        KeyValue bad2 = new NullKeyKeyValue();
        assertThatThrownBy(() -> KeyValues.of(Arrays.asList(bad1, bad2))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_singletonWithNullKeyElement_silentlySurvives() {
        // Documents the length-1 short-circuit: a single null-key KeyValue is
        // accepted without NPE. Compare to the 2+ case which NPEs in compareTo.
        // This is an additional contract confusion worth flagging.
        KeyValue bad = new NullKeyKeyValue();
        KeyValues kv = KeyValues.of(Collections.singletonList(bad));
        assertThat(kv).hasSize(1);
        // No NPE thrown — but getKey() on the element still returns null if anyone
        // ever iterates and reads it, which violates the KeyValue.getKey() contract.
    }

    /** Test-only {@link KeyValue} that returns {@code null} from {@link #getKey()}. */
    @SuppressWarnings("NullAway")
    private static final class NullKeyKeyValue implements KeyValue {

        @Override
        public String getKey() {
            return null;
        }

        @Override
        public String getValue() {
            return "v";
        }

    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_extractingFromElements_nullKeyExtractor_throwsNpe() {
        // Proves the NPE originates at ImmutableKeyValue.<init>, not at the
        // KeyValues.of entry point — i.e. fail-fast is NOT at entry.
        Object element = new Object();
        assertThatThrownBy(() -> KeyValues.of(Collections.singletonList(element), e -> null, e -> "v"))
            .isInstanceOf(NullPointerException.class)
            .hasStackTraceContaining("ImmutableKeyValue.<init>")
            .hasStackTraceContaining("KeyValue.of");
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_extractingFromElements_nullValueExtractor_throwsNpe() {
        Object element = new Object();
        assertThatThrownBy(() -> KeyValues.of(Collections.singletonList(element), e -> "k", e -> null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesAnd_iterableWithNullKeyExtractor_throwsNpe() {
        Object element = new Object();
        assertThatThrownBy(() -> KeyValues.empty().and(Collections.singletonList(element), e -> null, e -> "v"))
            .isInstanceOf(NullPointerException.class);
    }

    // --- (5) Sanity: the already-fixed paths still work (regression baseline) ---

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_stringVarargsSingleNull_returnsEmpty() {
        // gh-3851 fix: tolerated
        assertThat(KeyValues.of((String) null)).isSameAs(KeyValues.empty());
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_keyValueArraySingleNull_returnsEmpty() {
        // gh-3851 fix: tolerated
        assertThat(KeyValues.of((KeyValue) null)).isSameAs(KeyValues.empty());
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_iterableNull_returnsEmpty() {
        assertThat(KeyValues.of((Iterable<KeyValue>) null)).isSameAs(KeyValues.empty());
    }

    @Test
    @SuppressWarnings("NullAway")
    void keyValuesOf_stringVarargs_oddLengthThrowsIAE() {
        // Documents existing behavior: odd-length varargs is guarded at entry.
        assertThatThrownBy(() -> KeyValues.of("k1", "v1", "k2")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- (6) Headline: the two-path asymmetry, captured for the issue ---

    @Test
    @SuppressWarnings("NullAway")
    void summary_immutableVsValidated_areAsymmetricOnNullValue() {
        // Same user input "key + null value", two different factory methods, two
        // different results. This is the headline finding for the maintainer issue.
        Throwable immutableError = captureThrowable(() -> KeyValue.of("status", (String) null));
        assertThat(immutableError).isInstanceOf(NullPointerException.class);

        KeyValue fromValidated = KeyValue.of("status", (Object) null, v -> true);
        assertThat(fromValidated.getValue()).isEqualTo("null");
    }

    @SuppressWarnings("NullAway")
    private static Throwable captureThrowable(Runnable r) {
        try {
            r.run();
            return null;
        }
        catch (Throwable t) {
            return t;
        }
    }

    // --- (7) Layer 3 deep dive: KeyValue.of(null, "v", v -> true) ---

    // The original "reproduction" tests in this section documented the buggy
    // behavior (null key silently stored, hashCode/equals/toString worked, only
    // compareTo NPE'd). They have been replaced by the fix_expectation_* tests
    // in section (10), which assert the FIXED behavior.
    //
    // Historical record (pre-fix observations, kept as comments for the issue
    // PR discussion):
    //
    // KeyValue.of((String) null, "v", v -> true) — constructed without NPE,
    // getKey() returned null,
    // hashCode/equals/toString
    // all worked, only
    // compareTo threw NPE
    // (downstream, not at
    // construction).
    // new ValidatedKeyValue<>(null, "v", v -> true) — same; constructor had
    // no requireNonNull on key.

    // --- (8) ValidatedKeyValue constructor — fixed-behavior regression tests ---

    @Test
    @SuppressWarnings("NullAway")
    void validatedKeyValueConstructor_nullValue_validatorAccepts_storesStringNull() {
        // String.valueOf(null) → "null". This is the documented Java behavior,
        // not Micrometer-invented. The validator decided that null is acceptable,
        // and the literal string "null" is the stringified form.
        ValidatedKeyValue<String> kv = new ValidatedKeyValue<>("k", null, v -> true);
        assertThat(kv.getValue()).isEqualTo("null");
    }

    @Test
    @SuppressWarnings("NullAway")
    void validatedKeyValueConstructor_nullValue_validatorRejects_throwsIAE() {
        // Documents that the validator IS consulted for the value nullability,
        // proving the "validator decides" contract for value is real.
        assertThatThrownBy(() -> new ValidatedKeyValue<>("k", null, v -> false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- (9) Layer 2: KeyName null + validator (consistent with String path) ---

    @Test
    @SuppressWarnings("NullAway")
    void layer2_keyNameValidator_nullKeyName_throwsNpe() {
        // After the fix: both String and KeyName overloads throw NPE for null key.
        assertThatThrownBy(() -> KeyValue.of((KeyName) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void layer2_stringAndKeyNameOverloads_bothThrowNpeOnNullKey() {
        // After the fix: same logical "key = null" input → same exception type
        // (NullPointerException) on both overloads. The Layer 2 / Layer 3
        // divergence is resolved.
        assertThatThrownBy(() -> KeyValue.of((String) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> KeyValue.of((KeyName) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
    }

    // --- (10) Fix expectation: regression tests for the proposed 1-line fix ---

    @Test
    @SuppressWarnings("NullAway")
    void fix_expectation_validatedKeyValueConstructor_nullKey_throwsNpe() {
        // After the fix: ValidatedKeyValue constructor enforces non-null key
        // (consistent with @NullMarked contract on the package).
        assertThatThrownBy(() -> new ValidatedKeyValue<>(null, "v", v -> true))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void fix_expectation_keyValueOf_nullKeyWithValidator_throwsNpe() {
        // After the fix: public factory also throws on null key in the validator
        // overload. Previously this silently stored null as the key.
        assertThatThrownBy(() -> KeyValue.of((String) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway")
    void fix_expectation_keyValueOf_nullKeyNameWithValidator_throwsNpe() {
        // The KeyName overload already throws NPE at asString(). After the fix,
        // the String overload matches this — both should fail with the same
        // NullPointerException class.
        assertThatThrownBy(() -> KeyValue.of((KeyName) null, "v", v -> true)).isInstanceOf(NullPointerException.class);
    }

}
