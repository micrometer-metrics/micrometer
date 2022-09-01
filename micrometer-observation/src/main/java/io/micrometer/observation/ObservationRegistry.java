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

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.GlobalObservationConvention;
import io.micrometer.observation.Observation.ObservationConvention;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementations of this interface are responsible for managing state of an
 * {@link Observation}.
 *
 * @author Jonatan Ivanov
 * @author Tommy Ludwig
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface ObservationRegistry {

    /**
     * Creates an instance of {@link ObservationRegistry}.
     * @return {@link ObservationRegistry} instance
     */
    static ObservationRegistry create() {
        return new SimpleObservationRegistry();
    }

    /**
     * No-op implementation of {@link ObservationRegistry}.
     */
    ObservationRegistry NOOP = NoopObservationRegistry.INSTANCE;

    /**
     * When previously set will allow to retrieve the {@link Observation} at any point in
     * time.
     *
     * Example: if an {@link Observation} was put in {@link Observation.Scope} then this
     * method will return the current present {@link Observation} within the scope.
     * @return current observation or {@code null} if it's not present
     */
    @Nullable
    Observation getCurrentObservation();

    /**
     * When previously set will allow to retrieve the {@link Observation.Scope} at any
     * point in time.
     *
     * Example: if an {@link Observation} was put in {@link Observation.Scope} then this
     * method will return the current present {@link Observation.Scope}.
     * @return current observation scope or {@code null} if it's not present
     */
    @Nullable
    Observation.Scope getCurrentObservationScope();

    /**
     * Sets the observation scope as current.
     * @param current observation scope
     */
    void setCurrentObservationScope(@Nullable Observation.Scope current);

    /**
     * Configuration options for this registry.
     * @return observation configuration
     */
    ObservationConfig observationConfig();

    /**
     * Checks whether this {@link ObservationRegistry} is no-op.
     * @return {@code true} when this is a no-op observation registry
     */
    default boolean isNoop() {
        return this == NOOP;
    }

    /**
     * Access to configuration options for this registry.
     */
    class ObservationConfig {

        private final List<ObservationHandler<?>> observationHandlers = new CopyOnWriteArrayList<>();

        private final List<ObservationPredicate> observationPredicates = new CopyOnWriteArrayList<>();

        private final List<Observation.ObservationConvention<?>> globalObservationConventions = new CopyOnWriteArrayList<>();

        private final List<ObservationFilter> observationFilters = new CopyOnWriteArrayList<>();

        /**
         * Register a handler for the {@link Observation observations}.
         * @param handler handler to add to the current configuration
         * @return This configuration instance
         */
        public ObservationConfig observationHandler(ObservationHandler<?> handler) {
            this.observationHandlers.add(handler);
            return this;
        }

        /**
         * Register a predicate to define whether {@link Observation observation} should
         * be created or a {@link NoopObservation} instead.
         * @param predicate predicate
         * @return This configuration instance
         */
        public ObservationConfig observationPredicate(ObservationPredicate predicate) {
            this.observationPredicates.add(predicate);
            return this;
        }

        /**
         * Register an observation filter for the {@link Observation observations}.
         * @param observationFilter an observation filter to add to the current
         * configuration
         * @return This configuration instance
         */
        public ObservationConfig observationFilter(ObservationFilter observationFilter) {
            this.observationFilters.add(observationFilter);
            return this;
        }

        /**
         * Register {@link Observation.ObservationConvention observation conventions}.
         * @param globalObservationConventions global observation conventions
         * @return This configuration instance
         */
        public ObservationConfig observationConvention(
                Observation.GlobalObservationConvention<?>... globalObservationConventions) {
            this.globalObservationConventions.addAll(Arrays.asList(globalObservationConventions));
            return this;
        }

        /**
         * Register a collection of {@link Observation.ObservationConvention}.
         * @param globalObservationConventions global observation conventions
         * @return This configuration instance
         */
        public ObservationConfig observationConvention(
                Collection<Observation.GlobalObservationConvention<?>> globalObservationConventions) {
            this.globalObservationConventions.addAll(globalObservationConventions);
            return this;
        }

        /**
         * Finds an {@link Observation.ObservationConvention} for the given
         * {@link Observation.Context}.
         * <p>
         * When a {@link Observation.GlobalObservationConvention} is registered to the
         * given {@link Observation.Context}, this method returns a composite
         * {@link Observation.ObservationConvention} using the matched
         * {@link Observation.GlobalObservationConvention} and given
         * {@link Observation.ObservationConvention<T> defaultConvention}.
         * @param context context
         * @param defaultConvention default convention if none found
         * @return matching {@link Observation.ObservationConvention} or default when no
         * matching found
         */
        @SuppressWarnings("unchecked")
        public <T extends Observation.Context> Observation.ObservationConvention<T> getObservationConvention(T context,
                Observation.ObservationConvention<T> defaultConvention) {
            return this.globalObservationConventions.stream().filter(convention -> convention.supportsContext(context))
                    .findFirst()
                    .map(observationConvention -> (ObservationConvention<T>) new OverridingObservationConvention<>(
                            (GlobalObservationConvention<T>) observationConvention, defaultConvention))
                    .orElse(Objects.requireNonNull(defaultConvention,
                            "Default ObservationConvention must not be null"));
        }

        /**
         * Check to assert whether {@link Observation} should be created or
         * {@link NoopObservation} instead.
         * @param name observation technical name
         * @param context context
         * @return {@code true} when observation is enabled
         */
        public boolean isObservationEnabled(String name, @Nullable Observation.Context context) {
            return this.observationPredicates.stream().allMatch(predicate -> predicate.test(name, context));
        }

        // package-private for minimal visibility
        Collection<ObservationHandler<?>> getObservationHandlers() {
            return observationHandlers;
        }

        Collection<ObservationFilter> getObservationFilters() {
            return observationFilters;
        }

        Collection<Observation.ObservationConvention<?>> getObservationConventions() {
            return globalObservationConventions;
        }

    }

    /**
     * Composition of the multiple {@link Observation.ObservationConvention}.
     * <p>
     * This class takes a {@link Observation.GlobalObservationConvention} as a base and
     * add/override values with given {@link Observation.ObservationConvention
     * ObservationConventions}.
     *
     * @param <T> type of context
     */
    class OverridingObservationConvention<T extends Context> implements Observation.ObservationConvention<T> {

        private final GlobalObservationConvention<T> base;

        private final List<Observation.ObservationConvention<T>> conventions;

        /**
         * Create a composite {@link Observation.ObservationConvention}. This requires a
         * one base {@link Observation.GlobalObservationConvention}.
         * @param base base {@link Observation.GlobalObservationConvention convention}
         * @param conventions adding {@link Observation.ObservationConvention conventions}
         */
        @SafeVarargs
        public OverridingObservationConvention(GlobalObservationConvention<T> base,
                Observation.ObservationConvention<T>... conventions) {
            this.base = base;
            this.conventions = Arrays.asList(conventions);
        }

        /**
         * Combined low cardinality {@link KeyValues}.
         * @param context a context
         * @return low cardinality key values
         */
        @Override
        public KeyValues getLowCardinalityKeyValues(T context) {
            KeyValues keyValues = this.base.getLowCardinalityKeyValues(context);
            for (ObservationConvention<T> convention : this.conventions) {
                keyValues = KeyValues.concat(keyValues, convention.getLowCardinalityKeyValues(context));
            }
            return keyValues;
        }

        /**
         * Combined high cardinality {@link KeyValues}.
         * @param context a context
         * @return high cardinality key values
         */
        @Override
        public KeyValues getHighCardinalityKeyValues(T context) {
            KeyValues keyValues = this.base.getHighCardinalityKeyValues(context);
            for (ObservationConvention<T> convention : this.conventions) {
                keyValues = KeyValues.concat(keyValues, convention.getHighCardinalityKeyValues(context));
            }
            return keyValues;
        }

        /**
         * Returns {@code true} when all {@link Observation.ObservationConvention} returns
         * {@code true}.
         * @param context a {@link Context}
         * @return true when all conventions support the context
         */
        @Override
        public boolean supportsContext(Context context) {
            return this.base.supportsContext(context)
                    && this.conventions.stream().allMatch((convention) -> convention.supportsContext(context));
        }

        /**
         * The last {@link Observation.ObservationConvention} name.
         * @return name of the last convention
         */
        @Override
        public String getName() {
            if (this.conventions.isEmpty()) {
                return this.base.getName();
            }
            // use the last one
            return this.conventions.get(this.conventions.size() - 1).getName();
        }

        /**
         * The last {@link Observation.ObservationConvention} contextual name.
         * @param context context
         * @return contextual name of the last convention
         */
        @Override
        public String getContextualName(T context) {
            if (this.conventions.isEmpty()) {
                return this.base.getContextualName(context);
            }
            // use the last one
            return this.conventions.get(this.conventions.size() - 1).getContextualName(context);
        }

    }

}
