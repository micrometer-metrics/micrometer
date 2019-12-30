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

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link KeyNameGenerator} for Zabbix.
 */
public class ZabbixKeyNameGenerator implements KeyNameGenerator {

    @Override
    public String getKeyName(final String prefix, final String metricName, final String keySuffix,
                             final Iterable<Tag> tags, final String suffix) {

        StringBuilder keyName = new StringBuilder();
        if (notBlank(prefix)) {
            keyName.append(prefix).append(".");
        }
        keyName.append(metricName);
        if (tags.iterator().hasNext()) {
            keyName.append("[").append(stream(tags.spliterator(), false).map(Tag::getValue).collect(joining(",")));
            if (notBlank(keySuffix)) {
                keyName.append(",").append(keySuffix);
            }
            keyName.append("]");
        } else if (notBlank(keySuffix)) {
            keyName.append(".").append(keySuffix);
        }
        if (notBlank(suffix)) {
            keyName.append(".").append(suffix);
        }
        return keyName.toString();
    }

    /**
     * @param string The string to be checked.
     * @return {@code true} if the string contains non blank characters.
     */
    private boolean notBlank(String string) {
        return string != null && !string.trim().isEmpty();
    }
}
