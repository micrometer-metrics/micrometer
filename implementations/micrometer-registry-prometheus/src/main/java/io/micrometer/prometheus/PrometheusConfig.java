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

import io.micrometer.core.instrument.stats.hist.HistogramConfig;

public interface PrometheusConfig extends HistogramConfig {
    @Override
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
}
