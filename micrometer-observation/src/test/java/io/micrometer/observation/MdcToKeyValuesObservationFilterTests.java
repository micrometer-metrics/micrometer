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
import io.micrometer.observation.Observation.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MdcToKeyValuesObservationFilter}.
 *
 * @author Phil Clay
 */
class MdcToKeyValuesObservationFilterTests {

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        this.registry = ObservationRegistry.create();
        this.registry.observationConfig().observationHandler(context -> true);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private Context runWith(MdcToKeyValuesObservationFilter filter) {
        this.registry.observationConfig().observationFilter(filter);
        Context context = new Context();
        Observation.start("foo", () -> context, this.registry).stop();
        return context;
    }

    @Test
    void buildWithoutIncludeRulesThrows() {
        // A filter that can never include anything is a misconfiguration, so it is
        // rejected at build time rather than silently copying nothing.
        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().build())
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().excludeKey("a").build())
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(
                () -> MdcToKeyValuesObservationFilter.builder().excludeKeysMatching("a.*").excludeByDefault().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void includeKeyWithRenameAsLowCardinality() {
        MDC.put("accountId", "123");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("accountId", spec -> spec.lowCardinality().renameTo("account.id"))
            .build());

        assertThat(context.getLowCardinalityKeyValue("account.id")).isEqualTo(KeyValue.of("account.id", "123"));
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void includeKeyNoSpecDefaultsToHighCardinalityAndOriginalName() {
        MDC.put("accountId", "123");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder().includeKey("accountId").build());

        assertThat(context.getHighCardinalityKeyValue("accountId")).isEqualTo(KeyValue.of("accountId", "123"));
        assertThat(context.getLowCardinalityKeyValues()).isEmpty();
    }

    @Test
    void exactRuleWinsOverRegexRuleThatWouldAlsoMatch() {
        MDC.put("foobar", "1");
        MDC.put("fooX", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .excludeKey("foobar")
            .includeKeysMatching("foo(.*)", spec -> spec.renameTo("Foo$1"))
            .build());

        assertThat(context.getHighCardinalityKeyValue("FooX")).isEqualTo(KeyValue.of("FooX", "2"));
        // "foobar" is excluded by the exact rule even though it matches "foo(.*)"
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getValue).doesNotContain("1");
    }

    @Test
    void duplicateExactKeyFirstRuleWins() {
        MDC.put("k", "v");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("k", MdcToKeyValuesObservationFilter.ExactKeySpec::lowCardinality)
            .excludeKey("k")
            .build());

        assertThat(context.getLowCardinalityKeyValue("k")).isEqualTo(KeyValue.of("k", "v"));
        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void excludeKeysMatchingDropsMatchingKeys() {
        MDC.put("foobarbaz1", "a");
        MDC.put("other", "b");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .excludeKeysMatching("foobarbaz.*")
            .includeByDefault()
            .build());

        assertThat(context.getHighCardinalityKeyValue("other")).isEqualTo(KeyValue.of("other", "b"));
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getKey).doesNotContain("foobarbaz1");
    }

    @Test
    void regexRenameBackReferenceAndFullMatchAnchoring() {
        MDC.put("fooXYZ", "1");
        MDC.put("xfooXYZ", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKeysMatching("foo(.*)", spec -> spec.renameTo("Foo$1"))
            .build());

        assertThat(context.getHighCardinalityKeyValue("FooXYZ")).isEqualTo(KeyValue.of("FooXYZ", "1"));
        // "xfooXYZ" does not fully match "foo(.*)"
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getValue).doesNotContain("2");
    }

    @Test
    void includeByDefaultWithRenameUsing() {
        MDC.put("tenant", "t");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeByDefault(spec -> spec.renameUsing(key -> "other." + key))
            .build());

        assertThat(context.getHighCardinalityKeyValue("other.tenant")).isEqualTo(KeyValue.of("other.tenant", "t"));
    }

    @Test
    void excludeByDefaultDropsUnmatchedKeys() {
        MDC.put("a", "1");
        MDC.put("b", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder().includeKey("a").excludeByDefault().build());

        assertThat(context.getHighCardinalityKeyValue("a")).isEqualTo(KeyValue.of("a", "1"));
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getKey).doesNotContain("b");
    }

    @Test
    void keyMatchingNoRuleIsDropped() {
        MDC.put("a", "1");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder().includeKey("b").build());

        assertThat(context.getAllKeyValues()).isEmpty();
    }

    @Test
    void exactKeysOnlyFastPathSkipsExcludedAndUnconfiguredKeys() {
        MDC.put("a", "1");
        MDC.put("b", "2");
        MDC.put("c", "3");

        // Only exact rules + (default) exclude-by-default -> the per-key lookup
        // fast-path.
        Context context = runWith(MdcToKeyValuesObservationFilter.builder().includeKey("a").excludeKey("b").build());

        assertThat(context.getHighCardinalityKeyValue("a")).isEqualTo(KeyValue.of("a", "1"));
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getKey).doesNotContain("b", "c");
    }

    @Test
    void emptyMdcLeavesContextUnchanged() {
        Context context = runWith(MdcToKeyValuesObservationFilter.builder().includeByDefault().build());

        assertThat(context.getAllKeyValues()).isEmpty();
    }

    @Test
    void lowCardinalityRouting() {
        MDC.put("a", "1");
        MDC.put("b", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("a", MdcToKeyValuesObservationFilter.ExactKeySpec::lowCardinality)
            .includeByDefault()
            .build());

        assertThat(context.getLowCardinalityKeyValue("a")).isEqualTo(KeyValue.of("a", "1"));
        assertThat(context.getHighCardinalityKeyValue("b")).isEqualTo(KeyValue.of("b", "2"));
    }

    @Test
    void renameCollisionKeepsSingleEntry() {
        MDC.put("a", "1");
        MDC.put("b", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("a", spec -> spec.renameTo("same"))
            .includeKey("b", spec -> spec.renameTo("same"))
            .build());

        KeyValue keyValue = context.getHighCardinalityKeyValue("same");
        assertThat(keyValue).isNotNull();
        // iteration order of the MDC map is not guaranteed, so either value may win
        assertThat(keyValue.getValue()).isIn("1", "2");
        assertThat(context.getHighCardinalityKeyValues()).hasSize(1);
    }

    @Test
    void builderEnforcesPhaseOrdering() {
        // key rules cannot follow keysMatching rules
        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().includeKeysMatching("a.*").includeKey("b"))
            .isInstanceOf(IllegalStateException.class);

        // keysMatching rules cannot follow the default rule
        assertThatThrownBy(
                () -> MdcToKeyValuesObservationFilter.builder().includeByDefault().includeKeysMatching("a.*"))
            .isInstanceOf(IllegalStateException.class);

        // key rules cannot follow the default rule
        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().excludeByDefault().excludeKey("b"))
            .isInstanceOf(IllegalStateException.class);

        // the default rule may only be declared once
        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().includeByDefault().excludeByDefault())
            .isInstanceOf(IllegalStateException.class);

        // a valid in-order chain builds successfully
        MdcToKeyValuesObservationFilter.builder()
            .includeKey("a")
            .excludeKey("b")
            .includeKeysMatching("c.*")
            .excludeKeysMatching("d.*")
            .includeByDefault()
            .build();
    }

    @Test
    void renameUsingOnExactKey() {
        MDC.put("accountId", "123");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("accountId", spec -> spec.renameUsing(String::toUpperCase))
            .build());

        assertThat(context.getHighCardinalityKeyValue("ACCOUNTID")).isEqualTo(KeyValue.of("ACCOUNTID", "123"));
    }

    @Test
    void renameUsingOnKeysMatching() {
        MDC.put("fooBar", "1");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKeysMatching("foo.*", spec -> spec.renameUsing(key -> "mdc." + key))
            .build());

        assertThat(context.getHighCardinalityKeyValue("mdc.fooBar")).isEqualTo(KeyValue.of("mdc.fooBar", "1"));
    }

    @Test
    void renameUsingOnDefault() {
        MDC.put("tenant", "t");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeByDefault(spec -> spec.renameUsing(key -> key + ".value"))
            .build());

        assertThat(context.getHighCardinalityKeyValue("tenant.value")).isEqualTo(KeyValue.of("tenant.value", "t"));
    }

    @Test
    void renameUsingWinsWhenCalledAfterRenameTo() {
        MDC.put("accountId", "123");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("accountId", spec -> spec.renameTo("ignored").renameUsing(String::toUpperCase))
            .build());

        assertThat(context.getHighCardinalityKeyValue("ACCOUNTID")).isEqualTo(KeyValue.of("ACCOUNTID", "123"));
        assertThat(context.getHighCardinalityKeyValue("ignored")).isNull();
    }

    @Test
    void renameToWinsWhenCalledAfterRenameUsing() {
        MDC.put("accountId", "123");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKey("accountId", spec -> spec.renameUsing(String::toUpperCase).renameTo("account.id"))
            .build());

        assertThat(context.getHighCardinalityKeyValue("account.id")).isEqualTo(KeyValue.of("account.id", "123"));
        assertThat(context.getHighCardinalityKeyValue("ACCOUNTID")).isNull();
    }

    @Test
    @SuppressWarnings("NullAway")
    void renameUsingNullThrows() {
        assertThatThrownBy(
                () -> MdcToKeyValuesObservationFilter.builder().includeKey("a", spec -> spec.renameUsing(null)).build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void includeKeysMatchingPredicate() {
        MDC.put("fooBar", "1");
        MDC.put("baz", "2");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKeysMatching(key -> key.startsWith("foo"),
                    spec -> spec.lowCardinality().renameUsing(key -> "mdc." + key))
            .build());

        assertThat(context.getLowCardinalityKeyValue("mdc.fooBar")).isEqualTo(KeyValue.of("mdc.fooBar", "1"));
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getValue).doesNotContain("2");
    }

    @Test
    void excludeKeysMatchingPredicateThenIncludeByDefault() {
        MDC.put("secret.token", "x");
        MDC.put("tenant", "t");

        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .excludeKeysMatching(key -> key.startsWith("secret."))
            .includeByDefault()
            .build());

        assertThat(context.getHighCardinalityKeyValue("tenant")).isEqualTo(KeyValue.of("tenant", "t"));
        assertThat(context.getAllKeyValues()).extracting(KeyValue::getKey).doesNotContain("secret.token");
    }

    @Test
    void regexAndPredicateMatchingRulesEvaluateInOrder() {
        MDC.put("fooBar", "1");

        // The regex rule is declared first, so it wins over the later predicate rule.
        Context context = runWith(MdcToKeyValuesObservationFilter.builder()
            .includeKeysMatching("foo(.*)", spec -> spec.renameTo("regex.$1"))
            .includeKeysMatching(key -> key.startsWith("foo"), spec -> spec.renameUsing(key -> "predicate." + key))
            .build());

        assertThat(context.getHighCardinalityKeyValue("regex.Bar")).isEqualTo(KeyValue.of("regex.Bar", "1"));
        assertThat(context.getHighCardinalityKeyValue("predicate.fooBar")).isNull();
    }

    @Test
    void cachedKeyResolutionResolvesOncePerKey() {
        // A deliberately non-deterministic renamer lets us observe whether the regex rule
        // is re-resolved for a recurring key. With the cache on it should resolve once.
        AtomicInteger invocations = new AtomicInteger();
        MdcToKeyValuesObservationFilter filter = MdcToKeyValuesObservationFilter.builder()
            .includeKeysMatching("foo.*", spec -> spec.renameUsing(key -> key + "-" + invocations.incrementAndGet()))
            .build();
        this.registry.observationConfig().observationFilter(filter);
        MDC.put("fooX", "v");

        Context first = new Context();
        Observation.start("a", () -> first, this.registry).stop();
        Context second = new Context();
        Observation.start("b", () -> second, this.registry).stop();

        assertThat(invocations.get()).isEqualTo(1);
        assertThat(first.getHighCardinalityKeyValue("fooX-1")).isEqualTo(KeyValue.of("fooX-1", "v"));
        assertThat(second.getHighCardinalityKeyValue("fooX-1")).isEqualTo(KeyValue.of("fooX-1", "v"));
    }

    @Test
    void disabledKeyResolutionCacheReResolvesEachTime() {
        AtomicInteger invocations = new AtomicInteger();
        MdcToKeyValuesObservationFilter filter = MdcToKeyValuesObservationFilter.builder()
            .keyResolutionCache(spec -> spec.enabled(false))
            .includeKeysMatching("foo.*", spec -> spec.renameUsing(key -> key + "-" + invocations.incrementAndGet()))
            .build();
        this.registry.observationConfig().observationFilter(filter);
        MDC.put("fooX", "v");

        Context first = new Context();
        Observation.start("a", () -> first, this.registry).stop();
        Context second = new Context();
        Observation.start("b", () -> second, this.registry).stop();

        assertThat(invocations.get()).isEqualTo(2);
        assertThat(first.getHighCardinalityKeyValue("fooX-1")).isEqualTo(KeyValue.of("fooX-1", "v"));
        assertThat(second.getHighCardinalityKeyValue("fooX-2")).isEqualTo(KeyValue.of("fooX-2", "v"));
    }

    @Test
    void boundedCacheResolvesUncachedKeysEachTimeWhenFull() {
        AtomicInteger invocations = new AtomicInteger();
        MdcToKeyValuesObservationFilter filter = MdcToKeyValuesObservationFilter.builder()
            .keyResolutionCache(spec -> spec.maxSize(1))
            .includeKeysMatching("foo.*", spec -> spec.renameUsing(key -> key + "-" + invocations.incrementAndGet()))
            .build();
        this.registry.observationConfig().observationFilter(filter);

        // The single cache slot is filled by "fooA".
        MDC.put("fooA", "a");
        Context c1 = new Context();
        Observation.start("o1", () -> c1, this.registry).stop();
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(c1.getHighCardinalityKeyValue("fooA-1")).isEqualTo(KeyValue.of("fooA-1", "a"));

        // "fooB" cannot be cached (cache full) and is resolved on every observation,
        // while
        // "fooA" is still served from the cache.
        MDC.put("fooB", "b");
        Context c2 = new Context();
        Observation.start("o2", () -> c2, this.registry).stop();
        Context c3 = new Context();
        Observation.start("o3", () -> c3, this.registry).stop();

        assertThat(c2.getHighCardinalityKeyValue("fooA-1")).isEqualTo(KeyValue.of("fooA-1", "a"));
        assertThat(c3.getHighCardinalityKeyValue("fooA-1")).isEqualTo(KeyValue.of("fooA-1", "a"));
        assertThat(c2.getHighCardinalityKeyValue("fooB-2")).isEqualTo(KeyValue.of("fooB-2", "b"));
        assertThat(c3.getHighCardinalityKeyValue("fooB-3")).isEqualTo(KeyValue.of("fooB-3", "b"));
        assertThat(invocations.get()).isEqualTo(3);
    }

    @Test
    void keyResolutionCacheMaxSizeMustBePositive() {
        assertThatThrownBy(() -> MdcToKeyValuesObservationFilter.builder().keyResolutionCache(spec -> spec.maxSize(0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
