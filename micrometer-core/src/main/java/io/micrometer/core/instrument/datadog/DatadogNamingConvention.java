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
package io.micrometer.core.instrument.datadog;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;

/**
 * @author Jon Schneider
 */
public class DatadogNamingConvention implements NamingConvention {
    /**
     * {@see https://help.datadoghq.com/hc/en-us/articles/203764705-What-are-valid-metric-names-}
     *
     * Datadog's publish API will automatically strip Unicode without replacement. It will also replace
     * all non-alphanumeric characters with '_'.
     */
    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        String sanitized = NamingConvention.camelCase.name(name, type, baseUnit);

        // Metrics that don't start with a letter get dropped on the floor by the Datadog publish API,
        // so we will prepend them with 'm_'.
        if(!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }

        if(sanitized.length() > 200)
            return sanitized.substring(0, 200);
        return sanitized;
    }

    /**
     * Some set of non-alphanumeric characters will be replaced with '_', but not all (e.g. '/' is OK, but '{' is replaced).
     * Tag keys that begin with a number show up as an empty string, so we prepend them with 'm_'.
     */
    @Override
    public String tagKey(String key) {
        if(Character.isDigit(key.charAt(0))) {
            return "m_" + key;
        }
        return NamingConvention.camelCase.tagKey(key);
    }

    /**
     * Some set of non-alphanumeric characters will be replaced by Datadog automatically with '_', but not all
     * (e.g. '/' is OK, but '{' is replaced). It is permissible for a tag value to begin with a digit.
     */
    @Override
    public String tagValue(String value) {
        return value;
    }
}
