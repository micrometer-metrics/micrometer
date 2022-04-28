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
package io.micrometer.kairos;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for KairosDB.
 *
 * @author Anton Ilinchik
 * @since 1.1.0
 */
public class KairosNamingConvention implements NamingConvention {

    private static final Pattern BLACKLISTED_CHARS = Pattern.compile("[{}():,=\\[\\]]");

    private final NamingConvention delegate;

    public KairosNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public KairosNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    private String format(String name) {
        String normalized = StringEscapeUtils.escapeJson(name);
        return BLACKLISTED_CHARS.matcher(normalized).replaceAll("_");
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return format(delegate.name(name, type, baseUnit));
    }

    @Override
    public String tagKey(String key) {
        return format(delegate.tagKey(key));
    }

    @Override
    public String tagValue(String value) {
        return format(delegate.tagValue(value));
    }

}
