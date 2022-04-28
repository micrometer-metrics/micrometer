/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.dynatrace.v1.DynatraceNamingConventionV1;

/**
 * {@link NamingConvention} for Dynatrace. Delegates to the API-specific naming
 * convention.
 *
 * @author Oriol Barcelona Palau
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Georg Pirklbauer
 * @since 1.1.0
 */
public class DynatraceNamingConvention implements NamingConvention {

    private final NamingConvention versionSpecificNamingConvention;

    /**
     * Create a {@code DynatraceNamingConvention} instance.
     * @param delegate delegate {@link NamingConvention}
     * @param version Dynatrace API version
     * @since 1.8.0
     */
    public DynatraceNamingConvention(NamingConvention delegate, DynatraceApiVersion version) {
        if (version != DynatraceApiVersion.V1) {
            throw new IllegalArgumentException("At the moment, V1 is the only supported version");
        }
        this.versionSpecificNamingConvention = new DynatraceNamingConventionV1(delegate);
    }

    public DynatraceNamingConvention(NamingConvention delegate) {
        this(delegate, DynatraceApiVersion.V1);
    }

    public DynatraceNamingConvention() {
        this(NamingConvention.dot);
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return versionSpecificNamingConvention.name(name, type, baseUnit);
    }

    @Override
    public String name(String name, Meter.Type type) {
        return versionSpecificNamingConvention.name(name, type);
    }

    @Override
    public String tagKey(String key) {
        return versionSpecificNamingConvention.tagKey(key);
    }

    @Override
    public String tagValue(String value) {
        return versionSpecificNamingConvention.tagValue(value);
    }

}
