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
package io.micrometer.signalfx;

import java.util.regex.Pattern;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;

/**
 * {@link NamingConvention} for SignalFx.
 *
 * See https://developers.signalfx.com/metrics/data_ingest_overview.html#_criteria_for_metric_and_dimension_names_and_values
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class SignalFxNamingConvention implements NamingConvention {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(SignalFxNamingConvention.class);

    private static final Pattern START_UNDERSCORE_PATTERN = Pattern.compile("^_");
    private static final Pattern SF_PATTERN = Pattern.compile("^sf_");
    private static final Pattern START_LETTERS_PATTERN = Pattern.compile("^[a-zA-Z].*");
    private static final Pattern PATTERN_TAG_KEY_DENYLISTED_CHARS = Pattern.compile("[^\\w_\\-]");
    private static final Pattern PATTERN_TAG_KEY_DENYLISTED_PREFIX = Pattern.compile("^(aws|gcp|azure)_.*");

    private static final int NAME_MAX_LENGTH = 256;
    private static final int TAG_VALUE_MAX_LENGTH = 256;
    private static final int KEY_MAX_LENGTH = 128;

    private final NamingConvention delegate;

    public SignalFxNamingConvention() {
        this(NamingConvention.dot);
    }

    public SignalFxNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    // Metric (the metric name) can be any non-empty UTF-8 string, with a maximum length <= 256 characters
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String formattedName = StringEscapeUtils.escapeJson(delegate.name(name, type, baseUnit));
        return StringUtils.truncate(formattedName, NAME_MAX_LENGTH);
    }

    // 1. Has a maximum length of 128 characters
    // 2. May not start with _ or sf_
    // 3. Must start with a letter (upper or lower case). The rest of the name can contain letters, numbers, underscores _ and hyphens - . This requirement is expressed in the following regular expression:
    //     ^[a-zA-Z][a-zA-Z0-9_-]*$
    @Override
    public String tagKey(String key) {
        String conventionKey = delegate.tagKey(key);

        conventionKey = START_UNDERSCORE_PATTERN.matcher(conventionKey).replaceAll(""); // 2
        conventionKey = SF_PATTERN.matcher(conventionKey).replaceAll(""); // 2

        conventionKey = PATTERN_TAG_KEY_DENYLISTED_CHARS.matcher(conventionKey).replaceAll("_");
        if (!START_LETTERS_PATTERN.matcher(conventionKey).matches()) { // 3
            conventionKey = "a" + conventionKey;
        }
        if (PATTERN_TAG_KEY_DENYLISTED_PREFIX.matcher(conventionKey).matches()) {
            logger.log("'" + conventionKey + "' (original name: '" + key + "') is not a valid tag key. "
                    + "Must not start with any of these prefixes: aws_, gcp_, or azure_. "
                    + "Please rename it to conform to the constraints. "
                    + "If it comes from a third party, please use MeterFilter to rename it.");
        }
        return StringUtils.truncate(conventionKey, KEY_MAX_LENGTH); // 1
    }

    // Dimension value can be any non-empty UTF-8 string, with a maximum length <= 256 characters.
    @Override
    public String tagValue(String value) {
        String formattedValue = StringEscapeUtils.escapeJson(delegate.tagValue(value));
        return StringUtils.truncate(formattedValue, TAG_VALUE_MAX_LENGTH);
    }
}
