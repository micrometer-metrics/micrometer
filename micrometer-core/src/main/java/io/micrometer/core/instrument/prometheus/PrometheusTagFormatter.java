/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.prometheus;

import io.micrometer.core.instrument.TagFormatter;

import java.util.regex.Pattern;

/**
 * See https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
 * for a specification of the constraints on metric names and labels
 *
 * @author Jon Schneider
 */
public class PrometheusTagFormatter implements TagFormatter {
    private static final Pattern nameChars = Pattern.compile("[^a-zA-Z0-9_:]");
    private static final Pattern tagKeyChars = Pattern.compile("[^a-zA-Z0-9_]");

    /**
     * Names may contain ASCII letters and digits, as well as underscores and colons. They must match the regex
     * [a-zA-Z_:][a-zA-Z0-9_:]*
     */
    @Override
    public String formatName(String name) {
        String sanitized = nameChars.matcher(name).replaceAll("_");
        if(!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }
        return sanitized;
    }

    /**
     * Label names may contain ASCII letters, numbers, as well as underscores. They must match the regex
     * [a-zA-Z_][a-zA-Z0-9_]*. Label names beginning with __ are reserved for internal use.
     */
    @Override
    public String formatTagKey(String key) {
        String sanitized = tagKeyChars.matcher(key).replaceAll("_");
        if(!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }
        return sanitized;
    }

    /**
     * Label values may contain any Unicode characters.
     */
    @Override
    public String formatTagValue(String value) {
        return value;
    }
}
