/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.zabbix;

import io.micrometer.core.instrument.Tag;

/**
 * Generator for the key names of the metrics.
 */
public interface KeyNameGenerator {

    /**
     * @param prefix     prefix for the key.
     * @param metricName base name for the metric.
     * @param keySuffix  suffix that is appended after the key name
     * @param tags       tags for the key
     * @param suffix     suffix for the key
     * @return A key for a metric.
     */
    String getKeyName(final String prefix, final String metricName, final String keySuffix, final Iterable<Tag> tags,
                      final String suffix);

}
