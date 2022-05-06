/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for AppOptics.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public class AppOpticsNamingConvention implements NamingConvention {

    private static final int MAX_NAME_LENGTH = 255;

    private static final int MAX_TAG_KEY_LENGTH = 64;

    private static final int MAX_TAG_VALUE_LENGTH = 255;

    private static final Pattern NAME_BLACKLIST = Pattern.compile("[^-.:\\w]");

    private static final Pattern TAG_KEY_BLACKLIST = Pattern.compile("[^-.:\\w]");

    private static final Pattern TAG_VALUE_BLACKLIST = Pattern.compile("[^-.:\\w?\\\\/ ]");

    private final NamingConvention delegate;

    public AppOpticsNamingConvention() {
        this(NamingConvention.dot);
    }

    public AppOpticsNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String sanitized = NAME_BLACKLIST.matcher(delegate.name(name, type, baseUnit)).replaceAll("_");
        return StringUtils.truncate(sanitized, MAX_NAME_LENGTH);
    }

    @Override
    public String tagKey(String key) {
        String sanitized = TAG_KEY_BLACKLIST.matcher(delegate.tagKey(key)).replaceAll("_");
        return StringUtils.truncate(sanitized, MAX_TAG_KEY_LENGTH);
    }

    @Override
    public String tagValue(String value) {
        String sanitized = TAG_VALUE_BLACKLIST.matcher(delegate.tagValue(value)).replaceAll("_");
        return StringUtils.truncate(sanitized, MAX_TAG_VALUE_LENGTH);
    }

}
