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
package io.micrometer.atlas;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

/**
 * The naming convention most commonly employed at Netflix, and so most likely to show up
 * in Netflix examples.
 *
 * @author Jon Schneider
 */
public class AtlasNamingConvention implements NamingConvention {

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return NamingConvention.dot.name(name, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        if (key.equals("name")) {
            key = "name.tag";
        }
        else if (key.equals("statistic")) {
            key = "statistic.tag";
        }

        return NamingConvention.camelCase.tagKey(key);
    }

}
