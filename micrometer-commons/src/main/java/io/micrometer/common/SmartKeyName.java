/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.common;

import io.micrometer.common.docs.KeyName;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A smart {@link KeyName} that can extract values from context objects with automatic
 * fallback handling for null values. This helps ensure consistent key sets across
 * observations, which is required by some metrics backends like Prometheus.
 *
 * <p>
 * Example usage: <pre>{@code
 * static SmartKeyName<HttpRequest> METHOD = SmartKeyName.required("http.method", HttpRequest::getMethod);
 * static SmartKeyName<HttpRequest> USER_ID = SmartKeyName.optional("user.id", HttpRequest::getUserId);
 * static SmartKeyName<HttpRequest> REGION = SmartKeyName.withFallback("region", HttpRequest::getRegion, "unknown");
 *
 * // Usage - all at once
 * KeyValues keyValues = KeyValues.of(request, METHOD, USER_ID, REGION);
 *
 * // Usage - step by step
 * KeyValues keyValues = KeyValues.of(request, METHOD)
 *     .and(request, USER_ID)
 *     .and(request, REGION);
 * }</pre>
 *
 * @param <C> the type of context object from which to extract values
 * @author Yoobin Yoon
 * @since 1.16.0
 */
public class SmartKeyName<C> implements KeyName {

    private final String name;

    private final boolean required;

    private final Function<C, @Nullable Object> valueFunction;

    private final @Nullable String fallbackValue;

    private SmartKeyName(String name, boolean required, Function<C, @Nullable Object> valueFunction,
            @Nullable String fallbackValue) {
        this.name = name;
        this.required = required;
        this.valueFunction = valueFunction;
        this.fallbackValue = fallbackValue;
    }

    /**
     * Creates a required SmartKeyName. When the extracted value is null,
     * {@link KeyValue#NONE_VALUE} will be used.
     * @param name the key name
     * @param valueFunction function to extract value from context
     * @param <C> context type
     * @return a required SmartKeyName
     */
    public static <C> SmartKeyName<C> required(String name, Function<C, @Nullable Object> valueFunction) {
        return new SmartKeyName<>(name, true, valueFunction, null);
    }

    /**
     * Creates an optional SmartKeyName. When the extracted value is null, no KeyValue
     * will be created (returns null).
     * @param name the key name
     * @param valueFunction function to extract value from context
     * @param <C> context type
     * @return an optional SmartKeyName
     */
    public static <C> SmartKeyName<C> optional(String name, Function<C, @Nullable Object> valueFunction) {
        return new SmartKeyName<>(name, false, valueFunction, null);
    }

    /**
     * Creates a SmartKeyName with a custom fallback value. When the extracted value is
     * null, the fallback value will be used.
     * @param name the key name
     * @param valueFunction function to extract value from context
     * @param fallbackValue fallback value to use when extracted value is null
     * @param <C> context type
     * @return a SmartKeyName with custom fallback
     */
    public static <C> SmartKeyName<C> withFallback(String name, Function<C, @Nullable Object> valueFunction,
            String fallbackValue) {
        return new SmartKeyName<>(name, true, valueFunction, fallbackValue);
    }

    /**
     * Creates a KeyValue from the context, or returns null if the value should be
     * omitted.
     * @param context the context to extract value from (can be null)
     * @return KeyValue or null if the key should be omitted
     */
    public @Nullable KeyValue valueOf(@Nullable C context) {
        Object value = context != null ? valueFunction.apply(context) : null;
        if (value == null) {
            if (!required) {
                return null;
            }
            String finalValue = fallbackValue != null ? fallbackValue : KeyValue.NONE_VALUE;
            return KeyValue.of(this, finalValue);
        }
        return KeyValue.of(this, value.toString());
    }

    @Override
    public String asString() {
        return name;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the fallback value used when extracted value is null.
     * @return fallback value or null if using default NONE_VALUE
     */
    public @Nullable String getFallbackValue() {
        return fallbackValue;
    }

    @Override
    public String toString() {
        return "SmartKeyName{" + "name='" + name + '\'' + ", required=" + required + ", fallbackValue='" + fallbackValue
                + '\'' + '}';
    }

}
