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
package io.micrometer.prometheus;

import java.time.Duration;

public interface PrometheusConfig {
    default String prefix() {
        return "prometheus";
    }

    /**
     * {@code true} if meter descriptions should be sent to Prometheus.
     * Turn this off to minimize the amount of data sent on each scrape.
     */
    default boolean descriptions() {
        String v = get(prefix() + ".descriptions");
        return v == null || Boolean.valueOf(v);
    }

    /**
     * Get the value associated with a key.
     *
     * @param k
     *     Key to lookup in the config.
     * @return
     *     Value for the key or null if no key is present.
     */
    String get(String k);

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some max value.
     * This is used to limit the number of buckets shipped to Prometheus to save on storage.
     */
    default Duration timerPercentilesMax() {
        String v = get(prefix() + ".timerPercentilesMax");
        return v == null ? Duration.ofMinutes(2) : Duration.parse(v);
    }

    /**
     * A bucket filter clamping the bucket domain of timer percentiles histograms to some min value.
     * This is used to limit the number of buckets shipped to Prometheus to save on storage.
     */
    default Duration timerPercentilesMin() {
        String v = get(prefix() + ".timerPercentilesMin");
        return v == null ? Duration.ofMillis(10) : Duration.parse(v);
    }
}
