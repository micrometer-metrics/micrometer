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

import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

        private final List<Observation.GlobalKeyValuesProvider<?>> keyValuesProviders = new CopyOnWriteArrayList<>();

        private final List<ObservationFilter> observationFilters = new CopyOnWriteArrayList<>();

        private final Map<Class<? extends Observation.KeyValuesConvention>, Observation.KeyValuesConvention> keyValuesConventions = new ConcurrentHashMap<>();

        // TODO: To maintain backward compatibility
        private ObservationNamingConfiguration observationNamingConfiguration = ObservationNamingConfiguration.LEGACY;

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
         * Register a key values provider for the {@link Observation observations}.
         * @param keyValuesProvider key values provider to add to the current
         * configuration
         * @return This configuration instance
         */
        public ObservationConfig keyValuesProvider(Observation.GlobalKeyValuesProvider<?> keyValuesProvider) {
            this.keyValuesProviders.add(keyValuesProvider);
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
         * Register a key values provider for the {@link Observation observations}.
         * @param keyValuesConvention concrete key value convention
         * @return This configuration instance
         */
        @SuppressWarnings("unchecked")
        public ObservationConfig keyValuesConvention(Observation.KeyValuesConvention keyValuesConvention) {
            Class<?>[] interfaces = keyValuesConvention.getClass().getInterfaces();
            if (Arrays.stream(interfaces).noneMatch(Observation.KeyValuesConvention.class::isAssignableFrom)) {
                throw new IllegalArgumentException("The class [" + keyValuesConvention.getClass()
                        + "] does not implement any interface that extends from ["
                        + Observation.KeyValuesConvention.class + "]");
            }
            Class<?> keyValuesConventionInterface = interfaces[0];
            this.keyValuesConventions.put(
                    (Class<? extends Observation.KeyValuesConvention>) keyValuesConventionInterface,
                    keyValuesConvention);
            return this;
        }

        /**
         * Define how key values should be set.
         * @param keyValuesConvention setup for key values setting
         * @return This configuration instance
         */
        public ObservationConfig keyValuesConfiguration(ObservationNamingConfiguration keyValuesConvention) {
            this.observationNamingConfiguration = keyValuesConvention;
            return this;
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

        /**
         * Returns a registered key values convention for the given class.
         * @param clazz {@link Observation.KeyValuesConvention} class
         * @param <T> type of convention
         * @return registered convention
         * @throws {@link IllegalStateException} when no {@link Observation.KeyValuesConvention} found
         */
        @NonNull
        @SuppressWarnings("unchecked")
        public <T extends Observation.KeyValuesConvention> T getKeyValuesConvention(Class<T> clazz) {
            T t = (T) this.keyValuesConventions.get(clazz);
            if (t == null) {
                throw new IllegalStateException("No KeyValuesConvention found for class [" + clazz + "]");
            }
            return t;
        }

        /**
         * Returns the registered {@link ObservationNamingConfiguration}.
         * @return key values configuration
         */
        public ObservationNamingConfiguration getObservationNamingConfiguration() {
            return this.observationNamingConfiguration;
        }

        // package-private for minimal visibility
        Collection<ObservationHandler<?>> getObservationHandlers() {
            return observationHandlers;
        }

        Collection<Observation.GlobalKeyValuesProvider<?>> getKeyValuesProviders() {
            return keyValuesProviders;
        }

        Collection<ObservationFilter> getObservationFilters() {
            return observationFilters;
        }

    }

    // TODO: Which option should be the default?
    /**
     * Defines how tagging should take place.
     */
    enum ObservationNamingConfiguration {

        /**
         * Leaves the current behaviour of naming & tagging - will set the same tags as until now.
         * Backward-compatible approach.
         */
        LEGACY,

        /**
         * Creates two sets of observations - both the legacy together with the new standardized ones.
         * Backward-compatible approach useful for gradual.
         */
        LEGACY_WITH_STANDARDIZED,

        /**
         * Sets only the standardized names & tags. Backward-incompatible approach.
         */
        STANDARDIZED

    }

}
