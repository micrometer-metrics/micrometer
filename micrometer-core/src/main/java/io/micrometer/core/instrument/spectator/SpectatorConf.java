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
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.RegistryConfig;

import java.time.Duration;

public interface SpectatorConf extends RegistryConfig {
    /**
     * Property prefix to prepend to configuration names.
     */
    String prefix();

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some max value.
     * This is used to limit the number of buckets shipped to save on storage.
     */
    default Duration timerPercentilesMax() {
        String v = get(prefix() + ".timerPercentilesMax");
        return v == null ? Duration.ofMinutes(2) : Duration.parse(v);
    }

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some min value.
     * This is used to limit the number of buckets shipped to save on storage.
     */
    default Duration timerPercentilesMin() {
        String v = get(prefix() + ".timerPercentilesMin");
        return v == null ? Duration.ofMillis(10) : Duration.parse(v);
    }
}
