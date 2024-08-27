/*
 * Copyright 2024 VMware, Inc.
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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generator of observations bound to a static global registry. For use especially in
 * places where dependency injection of {@link ObservationRegistry} is not possible for an
 * instrumented type.
 *
 * @author Marcin Grzejszczak
 * @since 1.14.0
 */
public final class Observations {

    private static final ObservationRegistry initialRegistry = ObservationRegistry.create();

    private static final DelegatingObservationRegistry globalRegistry = new DelegatingObservationRegistry(
            initialRegistry);

    private Observations() {
    }

    /**
     * Sets a registry as the global registry.
     * @param registry Registry to set.
     */
    public static void setRegistry(ObservationRegistry registry) {
        globalRegistry.setDelegate(registry);
    }

    /**
     * Resets registry to the original, empty one.
     */
    public static void resetRegistry() {
        globalRegistry.setDelegate(initialRegistry);
    }

    /**
     * Retrieves the current global instance.
     * @return Global registry.
     */
    public static ObservationRegistry getGlobalRegistry() {
        return globalRegistry;
    }

    private static final class DelegatingObservationRegistry implements ObservationRegistry {

        private final AtomicReference<ObservationRegistry> delegate = new AtomicReference<>(ObservationRegistry.NOOP);

        DelegatingObservationRegistry(ObservationRegistry delegate) {
            setDelegate(delegate);
        }

        void setDelegate(ObservationRegistry delegate) {
            this.delegate.set(Objects.requireNonNull(delegate, "Delegate must not be null"));
        }

        @Nullable
        @Override
        public Observation getCurrentObservation() {
            return delegate.get().getCurrentObservation();
        }

        @Nullable
        @Override
        public Observation.Scope getCurrentObservationScope() {
            return delegate.get().getCurrentObservationScope();
        }

        @Override
        public void setCurrentObservationScope(@Nullable Observation.Scope current) {
            delegate.get().setCurrentObservationScope(current);
        }

        @Override
        public ObservationConfig observationConfig() {
            return delegate.get().observationConfig();
        }

        @Override
        public boolean isNoop() {
            return delegate.get().isNoop();
        }

    }

}
