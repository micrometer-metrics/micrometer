/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.signalfx;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.lang.Nullable;

/**
 * See https://developers.signalfx.com/reference#section-criteria-for-metric-and-dimension-names-and-values for criteria.
 *
 * @author Jon Schneider
 */
public class SignalFxNamingConvention implements NamingConvention {
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
        return formattedName.length() > 256 ? formattedName.substring(0, 256) : formattedName;
    }

    // 1. Has a maximum length of 128 characters
    // 2. May not start with _ or sf_
    // 3. Must start with a letter (upper or lower case). The rest of the name can contain letters, numbers, underscores _ and hyphens - . This requirement is expressed in the following regular expression:
    //     ^[a-zA-Z][a-zA-Z0-9_-]*$
    @Override
    public String tagKey(String key) {
        String conventionKey = delegate.tagKey(key);

        conventionKey = conventionKey.replaceAll("^_", "").replaceAll("^sf_", ""); // 2

        if (!conventionKey.matches("^[a-zA-Z].*")) { // 3
            conventionKey = "a" + conventionKey;
        }

        if (conventionKey.length() > 128) {
            conventionKey = conventionKey.substring(0, 128); // 1
        }

        return conventionKey;
    }

    // Dimension value can be any non-empty UTF-8 string, with a maximum length <= 256 characters.
    @Override
    public String tagValue(String value) {
        String formattedValue = StringEscapeUtils.escapeJson(delegate.tagValue(value));
        return formattedValue.length() > 256 ? formattedValue.substring(0, 256) : formattedValue;
    }
}
