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
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link ObservationFilter} that copies entries from the SLF4J {@link MDC Mapped
 * Diagnostic Context} into the {@link Observation.Context} as low or high cardinality
 * {@link KeyValue key values}.
 * <p>
 * Which MDC keys are copied, the cardinality level at which they are added, and whether
 * they are renamed is controlled by an ordered set of rules configured through the
 * {@link Builder}. For each MDC entry, the rules are consulted and the <em>first</em>
 * matching rule decides the outcome; an entry that matches no rule is dropped. Rules are
 * added in three phases that must be declared in order:
 * <ol>
 * <li>exact key rules ({@code includeKey}/{@code excludeKey})</li>
 * <li>matching rules, by regular expression or predicate
 * ({@code includeKeysMatching}/{@code excludeKeysMatching})</li>
 * <li>a single optional catch-all
 * ({@code includeByDefault}/{@code excludeByDefault})</li>
 * </ol>
 * <p>
 * At least one include rule must be defined; otherwise {@link Builder#build()} throws an
 * {@link IllegalStateException} since the filter would not copy any MDC entry.
 * <p>
 * Example: <pre>{@code
 * MdcToKeyValuesObservationFilter filter = MdcToKeyValuesObservationFilter.builder()
 *     // include a specific high-cardinality key, without renaming it
 *     .includeKey("order.id")
 *
 *     // include a specific high-cardinality key and rename it
 *     .includeKey("accountId", spec -> spec.renameTo("account.id"))
 *
 *     // include a specific low-cardinality key, without renaming it
 *     .includeKey("tenant", spec -> spec.lowCardinality())
 *
 *     // exclude a specific key that would otherwise be included by later rules
 *     .excludeKey("foobar")
 *
 *     // exclude a set of keys that would otherwise be included by later rules
 *     .excludeKeysMatching("foobarbaz.*")
 *
 *     // include a set of high-cardinality keys by regex,
 *     // and use back references to rename
 *     .includeKeysMatching("foo(.*)", spec -> spec.renameTo("Foo$1"))
 *
 *     // include a set of high-cardinality keys that start with a specific prefix
 *     .includeKeysMatching(key -> key.startsWith("order"))
 *
 *     // include the remaining keys as high-cardinality and rename them
 *     .includeByDefault(spec -> spec.renameUsing(key -> "mdc." + key))
 *
 *     .build();
 *
 * registry.observationConfig().observationFilter(filter);
 * }</pre>
 * <p>
 * Notes:
 * <ul>
 * <li>The MDC is thread local and is read on the thread that {@link Observation#stop()
 * stops} the observation. The configured rules, including any
 * {@link AbstractMdcKeySpec#renameUsing(UnaryOperator) rename functions}, run on that
 * same thread as part of stopping the observation, so they should be cheap and must not
 * throw.</li>
 * <li>Because the filter runs when the observation is stopped, only MDC entries that are
 * present at that moment can be copied. If the observation wraps code that populates the
 * MDC and then removes those entries before the observation stops (for example a key set
 * and cleared inside the observed scope), those values are no longer visible to the
 * filter and cannot be copied.</li>
 * <li>Included keys are added as high cardinality key values by default; use
 * {@link AbstractMdcKeySpec#lowCardinality()} to add them as low cardinality key values
 * instead.</li>
 * <li>Renaming is configured per rule. Exact-key and regular-expression rules accept
 * {@code renameTo} (a literal name for exact keys, a {@code $}-back-reference replacement
 * template for regular expressions) as well as {@code renameUsing}. Predicate and
 * catch-all default rules accept only {@code renameUsing}, since they can match many keys
 * and renaming them all to one literal name is rarely intended. {@code renameTo} and
 * {@code renameUsing} are mutually exclusive; the last one called wins.</li>
 * <li>If two MDC entries are renamed to the same key, the later one overwrites the
 * earlier one. However, the order of MDC entries is non-deterministic.</li>
 * <li>The resolution of regular-expression and default rules for a given MDC key is
 * cached so a recurring key does not repeatedly re-scan those rules. This cache is
 * enabled by default and bounded; it can be tuned or disabled through
 * {@link Builder#keyResolutionCache(Consumer) keyResolutionCache}.</li>
 * </ul>
 *
 * @author Phil Clay
 * @since 1.18.0
 */
public final class MdcToKeyValuesObservationFilter implements ObservationFilter {

    /**
     * Precomputed outcomes for exact key rules, keyed by MDC key name. Consulted first
     * and resolved with an O(1) lookup. Unmodifiable.
     */
    private final Map<String, Outcome> exactRules;

    /**
     * Regular-expression and predicate rules, in declared order. Consulted after the
     * exact rules for keys that no exact rule matched; the first matching rule wins.
     * Unmodifiable.
     */
    private final List<Rule> matchingRules;

    /**
     * The catch-all rule applied to keys matched by neither an exact nor a matching rule.
     * Defaults to {@link Rule#EXCLUDE_BY_DEFAULT}.
     */
    private final Rule defaultRule;

    /**
     * True when the only rules are exact key rules and unmatched keys are excluded. In
     * that case the output depends solely on the configured keys, so they can be looked
     * up directly instead of copying the whole MDC.
     */
    private final boolean exactKeysOnly;

    /**
     * Caches the resolved outcome for keys handled by the regex/predicate/default rules
     * (exact keys are already an O(1) lookup and stay out of the cache). {@code null}
     * when caching is disabled or unnecessary. Bounded to
     * {@link #keyResolutionCacheMaxSize} entries: once full, already-cached keys are
     * still served from the cache, but other keys are resolved on each lookup rather than
     * being added. Configure via {@link Builder#keyResolutionCache(Consumer)}.
     */
    private final @Nullable Map<String, Outcome> keyResolutionCache;

    /**
     * Soft maximum number of entries kept in {@link #keyResolutionCache}. It is a soft
     * bound because the size is checked before adding without locking, so under
     * concurrent stops the cache may exceed this by roughly the number of racing threads
     * before settling.
     */
    private final int keyResolutionCacheMaxSize;

    /**
     * Creates a filter from the rules and cache settings collected by the given builder.
     * @param builder the builder holding the configured rules and cache settings
     */
    private MdcToKeyValuesObservationFilter(Builder builder) {
        this.exactRules = Collections.unmodifiableMap(new HashMap<>(builder.exactRules));
        this.matchingRules = Collections.unmodifiableList(new ArrayList<>(builder.matchingRules));
        this.defaultRule = builder.defaultRule;
        this.exactKeysOnly = this.matchingRules.stream().noneMatch(rule -> rule.include) && !this.defaultRule.include;
        this.keyResolutionCache = (builder.keyResolutionCacheEnabled && !this.exactKeysOnly) ? new ConcurrentHashMap<>()
                : null;
        this.keyResolutionCacheMaxSize = builder.keyResolutionCacheMaxSize;
    }

    /**
     * Creates a new {@link Builder}.
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (this.exactKeysOnly) {
            // Fast path: the output depends only on the configured exact keys, so look
            // each one up directly instead of copying the whole MDC.
            for (Map.Entry<String, Outcome> entry : this.exactRules.entrySet()) {
                Outcome outcome = entry.getValue();
                // Skip the MDC lookup for keys an exclude rule would drop anyway.
                if (outcome.include) {
                    addIfIncluded(context, outcome, MDC.get(entry.getKey()));
                }
            }
            return context;
        }

        // Slow path: copy the MDC and resolve every entry through the rules.
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc == null || mdc.isEmpty()) {
            return context;
        }
        for (Map.Entry<String, String> entry : mdc.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null) {
                continue;
            }

            // Exact key rules always precede regex/predicate/default rules (enforced by
            // the builder), so an exact match can be resolved with an O(1) lookup. Other
            // keys fall through to the regex/predicate/default rules, whose resolution is
            // cached by key when caching is enabled.
            @Nullable Outcome outcome = this.exactRules.get(key);
            if (outcome == null) {
                outcome = resolveWithCache(key);
            }

            addIfIncluded(context, outcome, value);
        }
        return context;
    }

    /**
     * Resolves the outcome for {@code key} via the cache when enabled. On a miss, the key
     * is resolved and, while the cache is below its {@link #keyResolutionCacheMaxSize max
     * size}, added. Once the cache is full, uncached keys are resolved on every call.
     */
    private @Nullable Outcome resolveWithCache(String key) {
        Map<String, Outcome> cache = this.keyResolutionCache;
        if (cache == null) {
            return resolve(key);
        }
        Outcome cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Outcome resolved = resolve(key);
        if (resolved != null && cache.size() < this.keyResolutionCacheMaxSize) {
            cache.putIfAbsent(key, resolved);
        }
        return resolved;
    }

    /**
     * Resolves the outcome for a key not handled by an exact rule: the first matching
     * regex rule, otherwise the default rule. This is a pure function of the key, so its
     * result can be cached.
     */
    private @Nullable Outcome resolve(String key) {
        for (Rule rule : this.matchingRules) {
            Outcome outcome = rule.evaluate(key);
            if (outcome != null) {
                return outcome;
            }
        }
        return this.defaultRule.evaluate(key);
    }

    /**
     * Adds the MDC value to the context as a key value when the resolved outcome includes
     * it, at the cardinality the outcome dictates. This is the single place both the fast
     * and slow paths in {@link #map(Observation.Context)} funnel through, so the
     * include/cardinality decision cannot diverge between them.
     * @param context the context to add the key value to
     * @param outcome the resolved outcome, or {@code null} when no rule matched the key
     * @param value the MDC value, or {@code null} when the key has no value
     */
    private static void addIfIncluded(Observation.Context context, @Nullable Outcome outcome, @Nullable String value) {
        if (outcome == null || !outcome.include || value == null) {
            return;
        }
        KeyValue keyValue = KeyValue.of(outcome.newKey, value);
        if (outcome.lowCardinality) {
            context.addLowCardinalityKeyValue(keyValue);
        }
        else {
            context.addHighCardinalityKeyValue(keyValue);
        }
    }

    /**
     * Applies a rename operator to a key, returning the key unchanged when no operator is
     * configured.
     * @param renameOperator the rename operator, or {@code null} to keep the original key
     * @param key the original MDC key
     * @return the renamed key, or the original key when no operator is configured
     * @throws NullPointerException if the operator returns {@code null}
     */
    private static String applyRenameOperator(@Nullable UnaryOperator<String> renameOperator, String key) {
        if (renameOperator == null) {
            return key;
        }
        return Objects.requireNonNull(renameOperator.apply(key), "renameOperator must not return null");
    }

    /**
     * Builder for {@link MdcToKeyValuesObservationFilter}.
     * <p>
     * Rules must be declared in phases: all exact key rules first, then all matching
     * rules (regular expressions or predicates), then at most one catch-all default rule.
     * Declaring a rule out of order throws an {@link IllegalStateException}.
     */
    public static final class Builder {

        /**
         * The phases in which rules must be declared, in order. Used to enforce the exact
         * then matching then default ordering.
         */
        private enum Phase {

            /** Exact key rules ({@code includeKey}/{@code excludeKey}). */
            KEY,
            /** Matching rules by regular expression or predicate. */
            MATCHING,
            /** The single optional catch-all default rule. */
            DEFAULT

        }

        /**
         * Accumulates exact key outcomes, keyed by MDC key name; first declaration of a
         * key wins.
         */
        private final Map<String, Outcome> exactRules = new HashMap<>();

        /**
         * Accumulates regular-expression and predicate rules in declared order.
         */
        private final List<Rule> matchingRules = new ArrayList<>();

        /**
         * The catch-all default rule; excludes unmatched keys until configured otherwise.
         */
        private Rule defaultRule = Rule.EXCLUDE_BY_DEFAULT;

        /**
         * The current declaration phase, advanced as matching and default rules are
         * declared to enforce ordering.
         */
        private Phase phase = Phase.KEY;

        /**
         * Whether the key resolution cache is enabled on the built filter.
         */
        private boolean keyResolutionCacheEnabled = KeyResolutionCacheSpec.DEFAULT_ENABLED;

        /**
         * The soft maximum size of the key resolution cache on the built filter.
         */
        private int keyResolutionCacheMaxSize = KeyResolutionCacheSpec.DEFAULT_MAX_SIZE;

        /**
         * Creates an empty builder; obtain one via
         * {@link MdcToKeyValuesObservationFilter#builder()}.
         */
        private Builder() {
        }

        /**
         * Configures the cache that memoizes the resolution of regex/predicate/default
         * rules for a given MDC key, so a recurring key does not repeatedly re-scan those
         * rules. The cache is enabled by default with a maximum size of
         * {@value KeyResolutionCacheSpec#DEFAULT_MAX_SIZE}, which suits the typical case
         * of low MDC-key cardinality. Has no effect on exact key rules, which are always
         * an O(1) lookup. May be called at any point.
         * @param spec configures whether the cache is enabled and its maximum size
         * @return this builder
         * @see KeyResolutionCacheSpec
         */
        public Builder keyResolutionCache(Consumer<KeyResolutionCacheSpec> spec) {
            Objects.requireNonNull(spec, "spec must not be null");
            KeyResolutionCacheSpec cacheSpec = new KeyResolutionCacheSpec();
            spec.accept(cacheSpec);
            this.keyResolutionCacheEnabled = cacheSpec.enabled;
            this.keyResolutionCacheMaxSize = cacheSpec.maxSize;
            return this;
        }

        /**
         * Includes the given exact MDC key as a high cardinality key value, keeping its
         * name.
         * @param key the exact MDC key name to include
         * @return this builder
         */
        public Builder includeKey(String key) {
            return includeKey(key, spec -> {
            });
        }

        /**
         * Includes the given exact MDC key, configured by the given spec. An
         * {@link ExactKeySpec#renameTo(String) renameTo} value, if any, is a literal
         * name.
         * @param key the exact MDC key name to include
         * @param spec configures the cardinality and optional rename
         * @return this builder
         */
        public Builder includeKey(String key, Consumer<ExactKeySpec> spec) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            checkKeyPhase("includeKey");
            ExactKeySpec keySpec = applySpec(new ExactKeySpec(), spec);
            // Exact rule: the key is known now, so a renameTo template is its literal
            // value.
            String newKey = (keySpec.renameTemplate != null) ? keySpec.renameTemplate
                    : applyRenameOperator(keySpec.renameOperator, key);
            this.exactRules.putIfAbsent(key, new Outcome(true, keySpec.lowCardinality, newKey));
            return this;
        }

        /**
         * Excludes the given exact MDC key.
         * @param key the exact MDC key name to exclude
         * @return this builder
         */
        public Builder excludeKey(String key) {
            Objects.requireNonNull(key, "key must not be null");
            checkKeyPhase("excludeKey");
            this.exactRules.putIfAbsent(key, Outcome.EXCLUDED);
            return this;
        }

        /**
         * Includes MDC keys fully matching the given regular expression as high
         * cardinality key values, keeping their names.
         * @param regex the regular expression that an MDC key must fully match
         * @return this builder
         */
        public Builder includeKeysMatching(String regex) {
            return includeKeysMatching(regex, spec -> {
            });
        }

        /**
         * Includes MDC keys fully matching the given regular expression, configured by
         * the given spec. The rename, if any, may contain back-references (e.g.
         * {@code $1}) to groups captured by the regular expression.
         * @param regex the regular expression that an MDC key must fully match
         * @param spec configures the cardinality and optional rename
         * @return this builder
         */
        public Builder includeKeysMatching(String regex, Consumer<RegexKeySpec> spec) {
            Objects.requireNonNull(regex, "regex must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            checkMatchingPhase("includeKeysMatching");
            RegexKeySpec keySpec = applySpec(new RegexKeySpec(), spec);
            this.matchingRules.add(Rule.regex(Pattern.compile(regex), true, keySpec.lowCardinality,
                    keySpec.renameTemplate, keySpec.renameOperator));
            return this;
        }

        /**
         * Excludes MDC keys fully matching the given regular expression.
         * @param regex the regular expression that an MDC key must fully match
         * @return this builder
         */
        public Builder excludeKeysMatching(String regex) {
            Objects.requireNonNull(regex, "regex must not be null");
            checkMatchingPhase("excludeKeysMatching");
            this.matchingRules.add(Rule.regex(Pattern.compile(regex), false, false, null, null));
            return this;
        }

        /**
         * Includes MDC keys matching the given predicate as high cardinality key values,
         * keeping their names.
         * @param predicate tests whether an MDC key should be included
         * @return this builder
         */
        public Builder includeKeysMatching(Predicate<String> predicate) {
            return includeKeysMatching(predicate, spec -> {
            });
        }

        /**
         * Includes MDC keys matching the given predicate, configured by the given spec.
         * The spec offers no {@code renameTo}, since a predicate can match many keys and
         * renaming them all to one literal name is rarely intended; derive a name from
         * the key with {@link PredicateKeySpec#renameUsing(UnaryOperator) renameUsing}
         * instead.
         * <p>
         * The predicate is invoked on the thread that {@link Observation#stop() stops}
         * the observation, so it should be cheap and must not throw. It must also be a
         * pure function of the key, since its result may be memoized per distinct key by
         * the {@link #keyResolutionCache(Consumer) key resolution cache}.
         * @param predicate tests whether an MDC key should be included
         * @param spec configures the cardinality and optional rename
         * @return this builder
         */
        public Builder includeKeysMatching(Predicate<String> predicate, Consumer<PredicateKeySpec> spec) {
            Objects.requireNonNull(predicate, "predicate must not be null");
            Objects.requireNonNull(spec, "spec must not be null");
            checkMatchingPhase("includeKeysMatching");
            PredicateKeySpec keySpec = applySpec(new PredicateKeySpec(), spec);
            this.matchingRules.add(Rule.predicate(predicate, true, keySpec.lowCardinality, keySpec.renameOperator));
            return this;
        }

        /**
         * Excludes MDC keys matching the given predicate.
         * @param predicate tests whether an MDC key should be excluded
         * @return this builder
         */
        public Builder excludeKeysMatching(Predicate<String> predicate) {
            Objects.requireNonNull(predicate, "predicate must not be null");
            checkMatchingPhase("excludeKeysMatching");
            this.matchingRules.add(Rule.predicate(predicate, false, false, null));
            return this;
        }

        /**
         * Sets the default policy to include every MDC key not matched by an earlier
         * rule, as high cardinality key values keeping their names. Must be the last rule
         * declared.
         * @return this builder
         */
        public Builder includeByDefault() {
            return includeByDefault(spec -> {
            });
        }

        /**
         * Sets the default policy to include every MDC key not matched by an earlier
         * rule, configured by the given spec. The spec offers no {@code renameTo}, since
         * the default rule matches many keys and renaming them all to one literal name is
         * rarely intended; derive a name from the key with
         * {@link DefaultKeySpec#renameUsing(UnaryOperator) renameUsing} instead. Must be
         * the last rule declared.
         * @param spec configures the cardinality and optional rename
         * @return this builder
         */
        public Builder includeByDefault(Consumer<DefaultKeySpec> spec) {
            Objects.requireNonNull(spec, "spec must not be null");
            checkDefaultPhase();
            DefaultKeySpec keySpec = applySpec(new DefaultKeySpec(), spec);
            this.defaultRule = Rule.includeByDefault(keySpec.lowCardinality, keySpec.renameOperator);
            return this;
        }

        /**
         * Sets the default policy to exclude every MDC key not matched by an earlier
         * rule. This is already the implicit default of dropping unmatched keys but
         * states the intent explicitly. Must be the last rule declared.
         * @return this builder
         */
        public Builder excludeByDefault() {
            checkDefaultPhase();
            this.defaultRule = Rule.EXCLUDE_BY_DEFAULT;
            return this;
        }

        /**
         * Builds the {@link MdcToKeyValuesObservationFilter}.
         * @return a new filter
         * @throws IllegalStateException if no include rule was defined, since such a
         * filter could never copy any MDC entry
         */
        public MdcToKeyValuesObservationFilter build() {
            boolean includesNothing = this.exactRules.values().stream().noneMatch(outcome -> outcome.include)
                    && this.matchingRules.stream().noneMatch(rule -> rule.include) && !this.defaultRule.include;
            if (includesNothing) {
                throw new IllegalStateException("no include rules defined; add at least one of includeKey, "
                        + "includeKeysMatching, or includeByDefault");
            }
            return new MdcToKeyValuesObservationFilter(this);
        }

        /**
         * Applies the caller's configuration to the given spec and returns it.
         * @param keySpec the spec to configure
         * @param spec the caller-provided configuration
         * @param <S> the concrete spec type
         * @return the configured spec
         */
        private static <S extends AbstractMdcKeySpec<?>> S applySpec(S keySpec, Consumer<? super S> spec) {
            spec.accept(keySpec);
            return keySpec;
        }

        /**
         * Verifies an exact key rule is declared while still in the {@link Phase#KEY}
         * phase.
         * @param method the calling builder method, used in the error message
         * @throws IllegalStateException if a matching or default rule was already
         * declared
         */
        private void checkKeyPhase(String method) {
            if (this.phase != Phase.KEY) {
                throw new IllegalStateException(method
                        + " must be called before includeKeysMatching/excludeKeysMatching and includeByDefault/excludeByDefault");
            }
        }

        /**
         * Verifies a matching rule is declared before the default rule and advances the
         * phase to {@link Phase#MATCHING}.
         * @param method the calling builder method, used in the error message
         * @throws IllegalStateException if a default rule was already declared
         */
        private void checkMatchingPhase(String method) {
            if (this.phase == Phase.DEFAULT) {
                throw new IllegalStateException(method + " must be called before includeByDefault/excludeByDefault");
            }
            this.phase = Phase.MATCHING;
        }

        /**
         * Verifies the default rule has not already been declared and advances the phase
         * to {@link Phase#DEFAULT}.
         * @throws IllegalStateException if a default rule was already declared
         */
        private void checkDefaultPhase() {
            if (this.phase == Phase.DEFAULT) {
                throw new IllegalStateException("includeByDefault/excludeByDefault may only be called once");
            }
            this.phase = Phase.DEFAULT;
        }

    }

    /**
     * Base type for the per-rule key specs created by the {@link Builder}. Holds the
     * shared cardinality and {@link #renameUsing(UnaryOperator) rename-by-operator}
     * configuration. Each kind of rule has its own concrete subtype
     * ({@link ExactKeySpec}, {@link RegexKeySpec}, {@link PredicateKeySpec},
     * {@link DefaultKeySpec}); the two whose rules match a single nameable key, exact and
     * regex, additionally offer {@code renameTo}.
     *
     * @param <S> the concrete spec type, returned by the fluent methods so chained calls
     * keep the subtype
     */
    public abstract static class AbstractMdcKeySpec<S extends AbstractMdcKeySpec<S>> {

        /**
         * Whether the key is added as a low cardinality key value.
         * <ul>
         * <li>true = low cardinality</li>
         * <li>false = high cardinality (the default)</li>
         * </ul>
         *
         * Protected so the builder and subtypes can read and set it.
         */
        protected boolean lowCardinality;

        /**
         * The rename template set by {@link ExactKeySpec#renameTo(String)} (a literal
         * name) or {@link RegexKeySpec#renameTo(String)} (a replacement template), or
         * {@code null}. Always {@code null} for {@link PredicateKeySpec} and
         * {@link DefaultKeySpec}, which have no {@code renameTo}. Mutually exclusive with
         * {@link #renameOperator}.
         */
        protected @Nullable String renameTemplate;

        /**
         * The rename operator, or {@code null} to keep the original key. Mutually
         * exclusive with {@link #renameTemplate}.
         */
        protected @Nullable UnaryOperator<String> renameOperator;

        /**
         * Package private so the spec types cannot be subclassed or instantiated outside
         * this class.
         */
        AbstractMdcKeySpec() {
        }

        /**
         * Returns {@code this} as the concrete subtype, so the fluent methods keep the
         * subtype for chaining.
         * @return this spec as its concrete type
         */
        @SuppressWarnings("unchecked")
        private S self() {
            return (S) this;
        }

        /**
         * Adds the matched key as a high cardinality key value. This is the default.
         * @return this spec
         */
        public S highCardinality() {
            this.lowCardinality = false;
            return self();
        }

        /**
         * Adds the matched key as a low cardinality key value.
         * @return this spec
         */
        public S lowCardinality() {
            this.lowCardinality = true;
            return self();
        }

        /**
         * Renames the matched key using the given operator, applied to the original MDC
         * key, before it is added to the context. The operator must return a non-null key
         * and has no access to regex capture groups. Mutually exclusive with
         * {@link ExactKeySpec#renameTo(String)} and {@link RegexKeySpec#renameTo(String)}
         * (where available); the last of the two called wins.
         * <p>
         * The operator must be a pure function of the key: for matching and default rules
         * its result may be memoized per distinct key by the
         * {@link Builder#keyResolutionCache(Consumer) key resolution cache}, so it may be
         * invoked only once for a recurring key rather than on every observation. Like
         * the rest of the filter it runs on the thread that {@link Observation#stop()
         * stops} the observation, so it should be cheap and must not throw.
         * @param renamer maps the original MDC key to the new key name
         * @return this spec
         */
        public S renameUsing(UnaryOperator<String> renamer) {
            this.renameOperator = Objects.requireNonNull(renamer, "renamer must not be null");
            this.renameTemplate = null;
            return self();
        }

    }

    /**
     * Spec for an exact-key rule, configured via
     * {@link Builder#includeKey(String, Consumer) includeKey}.
     */
    public static final class ExactKeySpec extends AbstractMdcKeySpec<ExactKeySpec> {

        /**
         * Package private so it cannot be instantiated outside this class.
         */
        ExactKeySpec() {
        }

        /**
         * Renames the matched key to the given literal name before it is added to the
         * context. The name is used verbatim (no template substitution). To derive a name
         * from the key, use {@link #renameUsing(UnaryOperator)} instead. Mutually
         * exclusive with {@link #renameUsing(UnaryOperator)}; the last of the two called
         * wins.
         * @param name the literal new key name
         * @return this spec
         */
        public ExactKeySpec renameTo(String name) {
            this.renameTemplate = Objects.requireNonNull(name, "name must not be null");
            this.renameOperator = null;
            return this;
        }

    }

    /**
     * Spec for a regular-expression rule, configured via
     * {@link Builder#includeKeysMatching(String, Consumer) includeKeysMatching}.
     */
    public static final class RegexKeySpec extends AbstractMdcKeySpec<RegexKeySpec> {

        /**
         * Package private so it cannot be instantiated outside this class.
         */
        RegexKeySpec() {
        }

        /**
         * Renames the matched key using the given replacement template before it is added
         * to the context. The template may contain back-references to groups captured by
         * the regular expression ({@code $0} = whole key, {@code $1} = group 1,
         * &hellip;), so distinct keys can yield distinct names. To derive a name from the
         * key without using capture groups, use {@link #renameUsing(UnaryOperator)}
         * instead. Mutually exclusive with {@link #renameUsing(UnaryOperator)}; the last
         * of the two called wins.
         * @param replacementTemplate the regex replacement template
         * @return this spec
         */
        public RegexKeySpec renameTo(String replacementTemplate) {
            this.renameTemplate = Objects.requireNonNull(replacementTemplate, "replacementTemplate must not be null");
            this.renameOperator = null;
            return this;
        }

    }

    /**
     * Spec for a predicate rule, configured via
     * {@link Builder#includeKeysMatching(Predicate, Consumer) includeKeysMatching}. A
     * predicate can match many keys, so there is no {@code renameTo}; derive a name from
     * each key with {@link #renameUsing(UnaryOperator)}.
     */
    public static final class PredicateKeySpec extends AbstractMdcKeySpec<PredicateKeySpec> {

        /**
         * Package private so it cannot be instantiated outside this class.
         */
        PredicateKeySpec() {
        }

    }

    /**
     * Spec for the catch-all default rule, configured via
     * {@link Builder#includeByDefault(Consumer) includeByDefault}. The default rule
     * matches many keys, so there is no {@code renameTo}; derive a name from each key
     * with {@link #renameUsing(UnaryOperator)}.
     */
    public static final class DefaultKeySpec extends AbstractMdcKeySpec<DefaultKeySpec> {

        /**
         * Package private so it cannot be instantiated outside this class.
         */
        DefaultKeySpec() {
        }

    }

    /**
     * Configures the cache that memoizes regex/predicate/default rule resolution by MDC
     * key.
     */
    public static final class KeyResolutionCacheSpec {

        /**
         * Whether the key resolution cache is enabled by default.
         */
        public static final boolean DEFAULT_ENABLED = true;

        /**
         * Default {@link #maxSize(int) maximum number of keys} held in the cache.
         */
        public static final int DEFAULT_MAX_SIZE = 100;

        /**
         * Whether the cache is enabled.
         */
        private boolean enabled = DEFAULT_ENABLED;

        /**
         * The soft maximum number of keys held in the cache.
         */
        private int maxSize = DEFAULT_MAX_SIZE;

        /**
         * Creates a spec with the default settings ({@link #DEFAULT_ENABLED},
         * {@link #DEFAULT_MAX_SIZE}).
         */
        private KeyResolutionCacheSpec() {
        }

        /**
         * Sets whether the key resolution cache is enabled. Enabled by default.
         * @param enabled whether to cache rule resolution by key
         * @return this spec
         */
        public KeyResolutionCacheSpec enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the maximum number of keys held in the cache. Defaults to
         * {@value #DEFAULT_MAX_SIZE}. Once the cache is full, already-cached keys
         * continue to be served from the cache, but other keys are resolved on each
         * lookup rather than being added, bounding memory use when MDC-key cardinality is
         * unexpectedly high.
         * @param maxSize the maximum number of cached keys; must be at least {@code 1}
         * @return this spec
         */
        public KeyResolutionCacheSpec maxSize(int maxSize) {
            if (maxSize < 1) {
                throw new IllegalArgumentException("maxSize must be at least 1");
            }
            this.maxSize = maxSize;
            return this;
        }

    }

    /**
     * The resolved decision for an MDC key.
     */
    private static final class Outcome {

        /**
         * Shared outcome for keys that an exclude rule drops.
         */
        private static final Outcome EXCLUDED = new Outcome(false, false, "");

        /**
         * Whether the key is added to the context ({@code false} means dropped).
         */
        private final boolean include;

        /**
         * Whether to add the key as a low cardinality key value; high cardinality when
         * {@code false}. Unused when {@link #include} is {@code false}.
         */
        private final boolean lowCardinality;

        /**
         * The (possibly renamed) key to record. Empty when {@link #include} is
         * {@code false}.
         */
        private final String newKey;

        /**
         * Creates an outcome.
         * @param include whether the key is added to the context
         * @param lowCardinality whether to add it as a low cardinality key value
         * @param newKey the (possibly renamed) key to record
         */
        private Outcome(boolean include, boolean lowCardinality, String newKey) {
            this.include = include;
            this.lowCardinality = lowCardinality;
            this.newKey = newKey;
        }

    }

    /**
     * A matching rule, either regular-expression ({@code pattern != null}) or predicate
     * ({@code predicate != null}), or the catch-all default rule (both {@code null}).
     * Exact key rules are precomputed into a map and need no {@link Rule}.
     */
    private static final class Rule {

        /**
         * The default rule that drops every key not matched by an earlier rule.
         */
        private static final Rule EXCLUDE_BY_DEFAULT = new Rule(null, null, false, false, null, null);

        /**
         * The regular expression a key must fully match, or {@code null} for predicate
         * and default rules.
         */
        private final @Nullable Pattern pattern;

        /**
         * The predicate a key must satisfy, or {@code null} for regex and default rules.
         */
        private final @Nullable Predicate<String> predicate;

        /**
         * Whether a matched key is included ({@code false} excludes it).
         */
        private final boolean include;

        /**
         * Whether an included key is added as a low cardinality key value.
         */
        private final boolean lowCardinality;

        /**
         * The {@code $}-back-reference replacement template for a regex rule, or
         * {@code null}. Only regex rules carry a template (predicate and default rules
         * rename only via {@link #renameOperator}, since their specs have no
         * {@code renameTo}). Mutually exclusive with {@link #renameOperator}.
         */
        private final @Nullable String renameTemplate;

        /**
         * The rename operator applied to the original key, or {@code null}. Mutually
         * exclusive with {@link #renameTemplate}.
         */
        private final @Nullable UnaryOperator<String> renameOperator;

        /**
         * Creates a rule. Use the {@link #regex}, {@link #predicate} and
         * {@link #includeByDefault} factories rather than calling this directly.
         * @param pattern the regular expression, or {@code null}
         * @param predicate the predicate, or {@code null}
         * @param include whether a matched key is included
         * @param lowCardinality whether an included key is low cardinality
         * @param renameTemplate the rename template, or {@code null}
         * @param renameOperator the rename operator, or {@code null}
         */
        private Rule(@Nullable Pattern pattern, @Nullable Predicate<String> predicate, boolean include,
                boolean lowCardinality, @Nullable String renameTemplate,
                @Nullable UnaryOperator<String> renameOperator) {
            this.pattern = pattern;
            this.predicate = predicate;
            this.include = include;
            this.lowCardinality = lowCardinality;
            this.renameTemplate = renameTemplate;
            this.renameOperator = renameOperator;
        }

        /**
         * Creates a regular-expression rule.
         * @param pattern the regular expression a key must fully match
         * @param include whether a matched key is included
         * @param lowCardinality whether an included key is low cardinality
         * @param renameTemplate the rename template, or {@code null}
         * @param renameOperator the rename operator, or {@code null}
         * @return the rule
         */
        private static Rule regex(Pattern pattern, boolean include, boolean lowCardinality,
                @Nullable String renameTemplate, @Nullable UnaryOperator<String> renameOperator) {
            return new Rule(pattern, null, include, lowCardinality, renameTemplate, renameOperator);
        }

        /**
         * Creates a predicate rule. Predicate rules carry no rename template; any rename
         * is via {@code renameOperator}.
         * @param predicate the predicate a key must satisfy
         * @param include whether a matched key is included
         * @param lowCardinality whether an included key is low cardinality
         * @param renameOperator the rename operator, or {@code null}
         * @return the rule
         */
        private static Rule predicate(Predicate<String> predicate, boolean include, boolean lowCardinality,
                @Nullable UnaryOperator<String> renameOperator) {
            return new Rule(null, predicate, include, lowCardinality, null, renameOperator);
        }

        /**
         * Creates a catch-all default rule that includes every otherwise-unmatched key.
         * Carries no rename template; any rename is via {@code renameOperator}.
         * @param lowCardinality whether included keys are low cardinality
         * @param renameOperator the rename operator, or {@code null}
         * @return the rule
         */
        private static Rule includeByDefault(boolean lowCardinality, @Nullable UnaryOperator<String> renameOperator) {
            return new Rule(null, null, true, lowCardinality, null, renameOperator);
        }

        /**
         * Evaluates this rule against the given MDC key.
         * @param key the MDC key to evaluate
         * @return the {@link Outcome} when this rule matches the key (an include outcome
         * carrying the renamed key and cardinality, or {@link Outcome#EXCLUDED} for an
         * exclude rule), or {@code null} when the key does not match and the next rule
         * should be tried
         */
        private @Nullable Outcome evaluate(String key) {
            String newKey;
            if (this.pattern != null) {
                Matcher matcher = this.pattern.matcher(key);
                if (!matcher.matches()) {
                    return null;
                }
                if (!this.include) {
                    return Outcome.EXCLUDED;
                }
                if (this.renameTemplate != null) {
                    // Build the renamed key from the match captured by matches() via
                    // appendReplacement/appendTail, so the input is processed only once.
                    // (replaceFirst would reset the matcher and re-scan the input.)
                    StringBuffer renamed = new StringBuffer();
                    matcher.appendReplacement(renamed, this.renameTemplate);
                    matcher.appendTail(renamed);
                    newKey = renamed.toString();
                }
                else {
                    newKey = applyRenameOperator(this.renameOperator, key);
                }
            }
            else {
                // Predicate rule (predicate != null), or the catch-all default rule
                // (predicate == null) which always matches. Neither carries a rename
                // template, so any rename comes from the operator.
                if (this.predicate != null && !this.predicate.test(key)) {
                    return null;
                }
                if (!this.include) {
                    return Outcome.EXCLUDED;
                }
                newKey = applyRenameOperator(this.renameOperator, key);
            }
            return new Outcome(true, this.lowCardinality, newKey);
        }

    }

}
