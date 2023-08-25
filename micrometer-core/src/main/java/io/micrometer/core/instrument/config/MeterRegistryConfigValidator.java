/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.config;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.config.validate.ValidationException;

import java.util.Arrays;
import java.util.function.Function;

/**
 * @since 1.5.0
 */
public class MeterRegistryConfigValidator {

    private MeterRegistryConfigValidator() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SafeVarargs
    public static <M extends MeterRegistryConfig> Validated<?> checkAll(M config,
            Function<M, ? extends Validated<?>>... validation) {
        return Arrays.stream(validation)
            .map(v -> v.apply(config))
            .map(v -> (Validated<?>) v)
            .reduce(Validated.none(), (v1, v2) -> (Validated) v1.and(v2));
    }

    /**
     * Specifies how to retrieve a property on a configuration object, which in turn may
     * use {@link io.micrometer.core.instrument.config.validate.PropertyValidator} to
     * validate the format of the source of the property's value based on the
     * configuration's {@link MeterRegistryConfig#get(String)} implementation.
     * Alternatively the getter method used to fetch the property may be overridden
     * directly by a programmer as they instantiate the configuration interface.
     * @param property The named property to retrieve.
     * @param getter The method on the configuration interface which corresponds to this
     * property.
     * @param <M> The type of the configuration interface.
     * @param <T> The type of the property.
     * @return A function which, given a configuration instance, validates the property.
     */
    @SuppressWarnings("unchecked")
    public static <M extends MeterRegistryConfig, T> Function<M, Validated<T>> check(String property,
            Function<M, T> getter) {
        return config -> {
            try {
                return Validated.valid(config.prefix() + '.' + property, getter.apply(config));
            }
            catch (ValidationException e) {
                // for a single property, there should only be one validation failure
                return (Validated<T>) e.getValidation().failures().iterator().next();
            }
        };
    }

    public static <M extends MeterRegistryConfig, T> Function<M, Validated<T>> checkRequired(String property,
            Function<M, T> getter) {
        return check(property, getter).andThen(Validated::required);
    }

}
