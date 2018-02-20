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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.config.MeterRegistryConfig;

import java.time.Duration;

/**
 * Configuration for {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
public interface SimpleConfig extends MeterRegistryConfig {
    SimpleConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "simple";
    }

    /**
     * @return The step size (reporting frequency) to use.
     */
    default Duration step() {
        String v = get(prefix() + ".step");
        return v == null ? Duration.ofMinutes(1) : Duration.parse(v);
    }

    /**
     * @return A mode that determines whether the registry reports cumulative values over all time or
     * a rate normalized form representing changes in the last {@link #step()}.
     */
    default CountingMode mode() {
        String v = get(prefix() + ".mode");
        if (v == null)
            return CountingMode.CUMULATIVE;
        for (CountingMode countingMode : CountingMode.values()) {
            if (v.equalsIgnoreCase(countingMode.name()))
                return countingMode;
        }
        throw new IllegalArgumentException("Counting mode must be one of 'cumulative' or 'step' (check property " + prefix() + ".mode)");
    }
}
