/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_KEY_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_VALUE_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.METRIC_KEY_MAX_LENGTH;

/**
 * Naming convention for line protocol ingestion into Dynatrace
 * @see <a href="https://www.dynatrace.com/support/help/shortlink/metric-ingestion-protocol#dimension-optional">metric key naming convention</a>
 *
 * @author Oriol Barcelona
 * @author David Mass
 */
public class LineProtocolNamingConvention implements NamingConvention {

    private static final Pattern KEY_CLEANUP_PATTERN = Pattern.compile("[^a-z0-9-_.]");
    private static final Pattern NAME_CLEANUP_PATTERN = Pattern.compile("[^a-z0-9-_.]");

    private final NamingConvention delegate;

    public LineProtocolNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    public LineProtocolNamingConvention() {
        this(NamingConvention.dot);
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String conventionName = delegate.name(name, type, baseUnit);
        conventionName = conventionName.toLowerCase(Locale.US);
        String sanitized = NAME_CLEANUP_PATTERN.matcher(conventionName).replaceAll("_");
        try {
            return sanitized.substring(0,METRIC_KEY_MAX_LENGTH);
        }
        catch (IndexOutOfBoundsException e) {
            return sanitized;
        }
    }

    @Override
    public String tagKey(String key) {
        String conventionKey = delegate.tagKey(key);
        conventionKey = conventionKey.toLowerCase(Locale.US);
        String sanitized = KEY_CLEANUP_PATTERN.matcher(conventionKey).replaceAll("_");
        try {
            return sanitized.substring(0, DIMENSION_KEY_MAX_LENGTH);
        }
        catch (IndexOutOfBoundsException e) {
            return sanitized;
        }
    }

    @Override
    public String tagValue(String value) {
        return value.substring(0, DIMENSION_VALUE_MAX_LENGTH);
    }
}
