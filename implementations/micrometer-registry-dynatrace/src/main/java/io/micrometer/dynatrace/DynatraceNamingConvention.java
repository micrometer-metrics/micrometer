/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace;

import java.util.regex.Pattern;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

public class DynatraceNamingConvention implements NamingConvention {

    private static final Pattern KEY_CLEANUP_PATTERN = Pattern.compile("[^\\w.-]");
	
    private final NamingConvention delegate;

    public DynatraceNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    public DynatraceNamingConvention() {
        this(NamingConvention.dot);
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return "custom:" + delegate.name(name, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        return KEY_CLEANUP_PATTERN.matcher(delegate.tagKey(key)).replaceAll("_");
    }
}
