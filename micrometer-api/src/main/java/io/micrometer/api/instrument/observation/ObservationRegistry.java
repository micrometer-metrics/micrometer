/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.api.instrument.observation;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.NoopObservation;
import io.micrometer.api.lang.Nullable;

/**
 */
public interface ObservationRegistry {
    @Nullable
    Observation getCurrentObservation();

    void setCurrentObservation(@Nullable Observation current);

    /**
     * !!!!!!!!!!!!!!!!!!!! THIS IS NOT STARTED !!!!!!!!!!!!!!!!!!!!
     * !!!!!!!!!!!!!!!!!!!! REMEMBER TO CALL START() OTHERWISE YOU WILL FILE ISSUES THAT STUFF IS NOT WORKING !!!!!!!!!!!!!!!!!!!!
     * @param name observation name
     * @return this
     */
    default Observation observation(String name) {
        if (config().isObservationEnabled(name, null)) {
            return new SimpleObservation(name, this, new Observation.Context());
        }
        return NoopObservation.INSTANCE;
    }

    /**
     * !!!!!!!!!!!!!!!!!!!! THIS IS NOT STARTED !!!!!!!!!!!!!!!!!!!!
     * !!!!!!!!!!!!!!!!!!!! REMEMBER TO CALL START() OTHERWISE YOU WILL FILE ISSUES THAT STUFF IS NOT WORKING !!!!!!!!!!!!!!!!!!!!
     * @param name observation name
     * @param context context
     * @return this
     */
    default Observation observation(String name, Observation.Context context) {
        if (config().isObservationEnabled(name, context)) {
            return new SimpleObservation(name, this, context);
        }
        return NoopObservation.INSTANCE;
    }

    /**
     * Started observation.
     *
     * @param name observation name
     * @return this
     */
    default Observation start(String name) {
        return observation(name).start();
    }

    /**
     * Started observation.
     *
     * @param name observation name
     * @param context context
     * @return this
     */
    default Observation start(String name, Observation.Context context) {
        return observation(name, context).start();
    }

    Config config();

    /**
     * Access to configuration options for this registry.
     */
    class Config {

        private List<ObservationHandler<?>> observationHandlers = new CopyOnWriteArrayList<>();

        private List<BiPredicate<String, Observation.Context>> observationPredicates = new CopyOnWriteArrayList<>();

        /**
         * Register a handler for the {@link Observation observations}.
         *
         * @param handler handler to add to the current configuration
         * @return This configuration instance
         * @since 2.0.0
         */
        public ObservationRegistry.Config observationHandler(ObservationHandler<?> handler) {
            this.observationHandlers.add(handler);
            return this;
        }

        /**
         * Register a predicate to define whether {@link Observation observation} should be created or a
         * {@link NoopObservation} instead.
         *
         * @param predicate predicate
         * @return This configuration instance
         * @since 2.0.0
         */
        public ObservationRegistry.Config observationPredicate(BiPredicate<String, Observation.Context> predicate) {
            this.observationPredicates.add(predicate);
            return this;
        }

        /**
         * Check to assert whether {@link Observation} should be created or {@link NoopObservation} instead.
         *
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
    }
}
