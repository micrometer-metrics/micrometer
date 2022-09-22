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
package io.micrometer.dynatrace.v1;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for Dynatrace API v1.
 *
 * @author Oriol Barcelona Palau
 * @author Jon Schneider
 * @author Johnny Lim
 * @since 1.8.0
 */
public class DynatraceNamingConventionV1 implements NamingConvention {

    private static final WarnThenDebugLogger logger = new WarnThenDebugLogger(DynatraceNamingConventionV1.class);

    private static final Pattern NAME_CLEANUP_PATTERN = Pattern.compile("[^\\w._-]");

    private static final Pattern LEADING_NUMERIC_PATTERN = Pattern.compile("[._-]([\\d])+");

    private static final Pattern KEY_CLEANUP_PATTERN = Pattern.compile("[^\\w.-]");

    private static final int TAG_VALUE_MAX_LENGTH = 128;

    private final NamingConvention delegate;

    public DynatraceNamingConventionV1(NamingConvention delegate) {
        this.delegate = delegate;
    }

    public DynatraceNamingConventionV1() {
        this(NamingConvention.dot);
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return "custom:" + sanitizeName(delegate.name(name, type, baseUnit));
    }

    private String sanitizeName(String name) {
        if (name.equals("system.load.average.1m")) {
            return "system.load.average.oneminute";
        }
        String sanitized = NAME_CLEANUP_PATTERN.matcher(name).replaceAll("_");
        if (LEADING_NUMERIC_PATTERN.matcher(sanitized).find()) {
            logger.log(() -> "'" + sanitized + "' (original name: '" + name + "') is not a valid meter name. "
                    + "Dynatrace doesn't allow leading numeric characters after non-alphabets. "
                    + "Please rename it to conform to the constraints. "
                    + "If it comes from a third party, please use MeterFilter to rename it.");
        }
        return sanitized;
    }

    @Override
    public String tagKey(String key) {
        return KEY_CLEANUP_PATTERN.matcher(delegate.tagKey(key)).replaceAll("_");
    }

    @Override
    public String tagValue(String value) {
        return StringUtils.truncate(value, TAG_VALUE_MAX_LENGTH);
    }

}
