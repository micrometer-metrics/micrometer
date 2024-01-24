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
package io.micrometer.core.instrument.config.validate;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Validator for properties.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public class PropertyValidator {

    private PropertyValidator() {
    }

    public static Validated<Duration> getDuration(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        return DurationValidator.validate(prefixedProperty, config.get(prefixedProperty));
    }

    public static Validated<TimeUnit> getTimeUnit(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        return DurationValidator.validateTimeUnit(prefixedProperty, config.get(prefixedProperty));
    }

    public static Validated<Integer> getInteger(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        String value = config.get(prefixedProperty);

        try {
            return Validated.valid(prefixedProperty, value == null ? null : Integer.valueOf(value));
        }
        catch (NumberFormatException e) {
            return Validated.invalid(prefixedProperty, value, "must be an integer", InvalidReason.MALFORMED, e);
        }
    }

    public static <E extends Enum<E>> Validated<E> getEnum(MeterRegistryConfig config, Class<E> enumClass,
            String property) {
        String prefixedProperty = prefixedProperty(config, property);
        String value = config.get(prefixedProperty);

        if (value == null) {
            return Validated.valid(prefixedProperty, null);
        }

        try {
            @SuppressWarnings("unchecked")
            E[] values = (E[]) enumClass.getDeclaredMethod("values").invoke(enumClass);
            for (E enumValue : values) {
                if (enumValue.name().equalsIgnoreCase(value)) {
                    return Validated.valid(prefixedProperty, enumValue);
                }
            }

            return Validated.invalid(prefixedProperty, value,
                    "should be one of "
                            + Arrays.stream(values).map(v -> '\'' + v.name() + '\'').collect(Collectors.joining(", ")),
                    InvalidReason.MALFORMED);
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // indicates a bug in the meter registry's code, not in the user's
            // configuration
            throw new IllegalArgumentException(e);
        }
    }

    public static Validated<Boolean> getBoolean(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        String value = config.get(prefixedProperty);
        return Validated.valid(prefixedProperty, value == null ? null : Boolean.valueOf(value));
    }

    public static Validated<String> getSecret(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        return Validated.validSecret(prefixedProperty, config.get(prefixedProperty));
    }

    public static Validated<String> getString(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        return Validated.valid(prefixedProperty, config.get(prefixedProperty));
    }

    public static Validated<String> getUrlString(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        String value = config.get(prefixedProperty);

        try {
            return Validated.valid(prefixedProperty, value == null ? null : URI.create(value).toURL())
                .map(url -> value);
        }
        catch (MalformedURLException | IllegalArgumentException ex) {
            return Validated.invalid(prefixedProperty, value, "must be a valid URL", InvalidReason.MALFORMED, ex);
        }
    }

    /**
     * Get a validated URI string.
     * @param config config
     * @param property property
     * @return a validated URI string
     * @see #getUrlString(MeterRegistryConfig, String)
     * @see <a href=
     * "https://github.com/micrometer-metrics/micrometer/issues/3903">gh-3903</a>
     * @since 1.9.14
     */
    public static Validated<String> getUriString(MeterRegistryConfig config, String property) {
        String prefixedProperty = prefixedProperty(config, property);
        String value = config.get(prefixedProperty);

        try {
            return Validated.valid(prefixedProperty, value == null ? null : URI.create(value)).map(uri -> value);
        }
        catch (IllegalArgumentException ex) {
            return Validated.invalid(prefixedProperty, value, "must be a valid URI", InvalidReason.MALFORMED, ex);
        }
    }

    private static String prefixedProperty(MeterRegistryConfig config, String property) {
        return config.prefix() + '.' + property;
    }

}
