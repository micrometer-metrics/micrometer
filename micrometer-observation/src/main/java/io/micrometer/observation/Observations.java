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

    private static ObservationRegistry globalRegistry = initialRegistry;

    private Observations() {
        throw new UnsupportedOperationException("You can't instantiate a utility class");
    }

    /**
     * Sets a registry as the global registry.
     * @param registry Registry to set.
     */
    public static void setRegistry(ObservationRegistry registry) {
        globalRegistry = registry;
    }

    /**
     * Resets registry to the original, empty one.
     */
    public static void resetRegistry() {
        globalRegistry = initialRegistry;
    }

    /**
     * Retrieves the current global instance.
     * @return Global registry.
     */
    public static ObservationRegistry getGlobalRegistry() {
        return globalRegistry;
    }

}
