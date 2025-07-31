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

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.annotation.Incubating;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validator for {@link Duration}.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public enum DurationValidator {

    /**
     * Human readable formatting, for example '1s'.
     */
    SIMPLE("^\\s*(([\\+]?\\d+)(\\.\\d*)?)\\s*([a-zA-Z]{0,2})\\s*",
            "^\\s*([\\+]?\\d{0,3}([_,]?\\d{3})*(\\.\\d*)?)\\s*([a-zA-Z]{0,2})\\s*") {
        @Override
        protected Validated<Duration> doParse(String property, String value) {
            Matcher matcher = patterns.get(0).matcher(value.toLowerCase(Locale.ROOT).replaceAll("[,_\\s]", ""));
            if (!matcher.matches()) {
                return Validated.invalid(property, value, "must be a valid duration", InvalidReason.MALFORMED);
            }

            String unit = matcher.group(4);
            if (StringUtils.isBlank(unit)) {
                return Validated.invalid(property, value, "must have a valid duration unit", InvalidReason.MALFORMED);
            }

            int e = 0;
            Double amount = Double.valueOf(matcher.group(1));
            while (e < 18 && Math.abs(amount - amount.longValue()) > 1e-10) {
                amount *= 10;
                e++;
            }
            long multipliedResult = amount.longValue();
            long multipliedFactor = (long) Math.pow(10, e);
            return validateChronoUnit(property, value, unit)
                .map(cu -> Duration.of(multipliedResult, cu).dividedBy(multipliedFactor));
        }
    },

    ISO8601("^[\\+\\-]?P.*$") {
        @Override
        protected Validated<Duration> doParse(String property, String value) {
            try {
                return Validated.valid(property, Duration.parse(value));
            }
            catch (Exception ex) {
                return Validated.invalid(property, value, "must be a valid ISO-8601 duration like 'PT10S'",
                        InvalidReason.MALFORMED, ex);
            }
        }
    };

    protected final List<Pattern> patterns;

    DurationValidator(String... patterns) {
        this.patterns = Arrays.stream(patterns).map(Pattern::compile).collect(Collectors.toList());
    }

    /**
     * Detect the style then parse the value to return a duration.
     * @param property The configuration property this duration belongs to
     * @param value The value to parse
     * @return the parsed duration
     */
    public static Validated<Duration> validate(String property, @Nullable String value) {
        return value == null ? Validated.valid(property, null)
                : detect(property, value).flatMap(validator -> validator.doParse(property, value));
    }

    /**
     * Parse the given value to a duration.
     * @param property The configuration property this duration belongs to
     * @param value The value to parse
     * @return a duration
     */
    protected abstract Validated<Duration> doParse(String property, String value);

    /**
     * Detect the style from the given source value.
     * @param value the source value
     * @return the duration style
     */
    private static Validated<DurationValidator> detect(String property, @Nullable String value) {
        if (value == null || StringUtils.isBlank(value)) {
            return Validated.invalid(property, value, "must be a valid duration value",
                    value == null ? InvalidReason.MISSING : InvalidReason.MALFORMED);
        }

        for (DurationValidator candidate : values()) {
            if (candidate.patterns.stream().anyMatch(p -> p.matcher(value).matches())) {
                return Validated.valid(property, candidate);
            }
        }

        return Validated.invalid(property, value, "must be a valid duration value", InvalidReason.MALFORMED);
    }

    public static Validated<TimeUnit> validateTimeUnit(String property, @Nullable String unit) {
        return validateChronoUnit(property, unit, unit).flatMap(cu -> toTimeUnit(property, cu));
    }

    /**
     * Validate a unit that is potentially part of a larger string including the magnitude
     * of time.
     * @param property The property that is a {@link Duration} or unit.
     * @param value The whole string including magnitude.
     * @param unit The unit portion of the string.
     * @return A validated unit.
     */
    public static Validated<ChronoUnit> validateChronoUnit(String property, @Nullable String value,
            @Nullable String unit) {
        if (unit == null) {
            return Validated.valid(property, null);
        }

        switch (unit.toLowerCase(Locale.ROOT)) {
            case "ns":
            case "nanoseconds":
            case "nanosecond":
            case "nanos":
                return Validated.valid(property, ChronoUnit.NANOS);
            case "us":
            case "microseconds":
            case "microsecond":
            case "micros":
                return Validated.valid(property, ChronoUnit.MICROS);
            case "ms":
            case "milliseconds":
            case "millisecond":
            case "millis":
                return Validated.valid(property, ChronoUnit.MILLIS);
            case "s":
            case "seconds":
            case "second":
            case "secs":
            case "sec":
                return Validated.valid(property, ChronoUnit.SECONDS);
            case "m":
            case "minutes":
            case "minute":
            case "mins":
            case "min":
                return Validated.valid(property, ChronoUnit.MINUTES);
            case "h":
            case "hours":
            case "hour":
                return Validated.valid(property, ChronoUnit.HOURS);
            case "d":
            case "days":
            case "day":
                return Validated.valid(property, ChronoUnit.DAYS);
            default:
                return Validated.invalid(property, value, "must contain a valid time unit", InvalidReason.MALFORMED);
        }
    }

    private static Validated<TimeUnit> toTimeUnit(String property, @Nullable ChronoUnit chronoUnit) {
        if (chronoUnit == null) {
            return Validated.valid(property, null);
        }

        switch (chronoUnit) {
            case NANOS:
                return Validated.valid(property, TimeUnit.NANOSECONDS);
            case MICROS:
                return Validated.valid(property, TimeUnit.MICROSECONDS);
            case MILLIS:
                return Validated.valid(property, TimeUnit.MILLISECONDS);
            case SECONDS:
                return Validated.valid(property, TimeUnit.SECONDS);
            case MINUTES:
                return Validated.valid(property, TimeUnit.MINUTES);
            case HOURS:
                return Validated.valid(property, TimeUnit.HOURS);
            case DAYS:
                return Validated.valid(property, TimeUnit.DAYS);
            default:
                return Validated.invalid(property, chronoUnit.toString(), "must be a valid time unit",
                        InvalidReason.MALFORMED);
        }
    }

}
