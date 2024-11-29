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

import io.micrometer.common.lang.Nullable;

import io.micrometer.observation.Observation.ObservationLevel;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

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
    ObservationRegistry NOOP = new NoopObservationRegistry();

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

        private final List<ObservationConvention<?>> observationConventions = new CopyOnWriteArrayList<>();

        private final List<ObservationFilter> observationFilters = new CopyOnWriteArrayList<>();

        private final Map<String, Level> observationLevels = new ConcurrentHashMap<>();

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
         * Register an {@link ObservationConvention}.
         * <p>
         * Please check the javadoc of
         * {@link Observation#createNotStarted(ObservationConvention, ObservationConvention, Supplier, ObservationRegistry)}
         * method for the logic of choosing the convention.
         * </p>
         * @param observationConvention observation convention
         * @return This configuration instance
         */
        public ObservationConfig observationConvention(GlobalObservationConvention<?> observationConvention) {
            this.observationConventions.add(observationConvention);
            return this;
        }

        /**
         * Sets an observation level for the given observation name.
         * @param observationName observation name
         * @param level observation level
         * @return This configuration instance
         */
        public ObservationConfig observationLevel(String observationName, Level level) {
            this.observationLevels.put(observationName, level);
            return this;
        }

        /**
         * Sets observation levels.
         * @param levels observation levels (observation name to level mappings)
         * @return This configuration instance
         */
        public ObservationConfig observationLevels(Map<String, Level> levels) {
            this.observationLevels.putAll(levels);
            return this;
        }

        /**
         * Finds an {@link ObservationConvention} for the given
         * {@link Observation.Context}.
         * @param context context
         * @param defaultConvention default convention if none found
         * @return matching {@link ObservationConvention} or default when no matching
         * found
         */
        @SuppressWarnings("unchecked")
        <T extends Observation.Context> ObservationConvention<T> getObservationConvention(T context,
                ObservationConvention<T> defaultConvention) {
            for (ObservationConvention<?> convention : this.observationConventions) {
                if (convention.supportsContext(context)) {
                    return (ObservationConvention<T>) convention;
                }
            }
            return Objects.requireNonNull(defaultConvention, "Default ObservationConvention must not be null");
        }

        /**
         * Check to assert whether {@link Observation} should be created or
         * {@link NoopObservation} instead.
         * @param name observation technical name
         * @param context context
         * @return {@code true} when observation is enabled
         */
        boolean isObservationEnabled(String name, @Nullable Observation.Context context) {
            for (ObservationPredicate predicate : this.observationPredicates) {
                if (!predicate.test(name, context)) {
                    return false;
                }
            }
            if (context != null) {
                ObservationLevel level = context.getLevel();
                if (level == null) {
                    return true;
                }
                String observationName = context.getName();
                for (Entry<String, Level> levelEntry : this.observationLevels.entrySet()) {
                    if (levelEntry.getKey().equalsIgnoreCase(observationName)) {
                        // exact or partial match
                        // e.g. ctx has INFO (3), configured is DEBUG (2)
                        return level.getLevel().ordinal() >= levelEntry.getValue().ordinal();
                    }
                }
            }
            return true;
        }

        // package-private for minimal visibility
        Collection<ObservationHandler<?>> getObservationHandlers() {
            return observationHandlers;
        }

        Collection<ObservationFilter> getObservationFilters() {
            return observationFilters;
        }

        Collection<ObservationConvention<?>> getObservationConventions() {
            return observationConventions;
        }

    }

}
