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
package io.micrometer.datadog;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;

/**
 * {@link NamingConvention} for Datadog.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class DatadogNamingConvention implements NamingConvention {

    private static final int MAX_NAME_LENGTH = 200;

    private final NamingConvention delegate;

    public DatadogNamingConvention() {
        this(NamingConvention.dot);
    }

    public DatadogNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    /**
     * See:
     * https://help.datadoghq.com/hc/en-us/articles/203764705-What-are-valid-metric-names-
     * <p>
     * Datadog's publish API will automatically strip Unicode without replacement. It will
     * also replace all non-alphanumeric characters with '_'.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        // forward slashes, even URL encoded, blow up the POST metadata API
        String sanitized = StringEscapeUtils.escapeJson(delegate.name(name, type, baseUnit).replace('/', '_'));

        // Metrics that don't start with a letter get dropped on the floor by the Datadog
        // publish API,
        // so we will prepend them with 'm.'.
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m." + sanitized;
        }
        return StringUtils.truncate(sanitized, MAX_NAME_LENGTH);
    }

    /**
     * Some set of non-alphanumeric characters will be replaced with '_', but not all
     * (e.g. '/' is OK, but '{' is replaced). Tag keys that begin with a number show up as
     * an empty string, so we prepend them with 'm.'.
     */
    @Override
    public String tagKey(String key) {
        String sanitized = StringEscapeUtils.escapeJson(delegate.tagKey(key));
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "m." + sanitized;
        }
        return sanitized;
    }

    /**
     * Some set of non-alphanumeric characters will be replaced by Datadog automatically
     * with '_', but not all (e.g. '/' is OK, but '{' is replaced). It is permissible for
     * a tag value to begin with a digit.
     */
    @Override
    public String tagValue(String value) {
        return StringEscapeUtils.escapeJson(delegate.tagValue(value));
    }

}
