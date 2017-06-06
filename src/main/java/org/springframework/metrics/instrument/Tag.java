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
package org.springframework.metrics.instrument;

import org.springframework.metrics.instrument.internal.ImmutableTag;

import java.util.Arrays;
import java.util.List;

/**
 * Key/value pair representing a dimension of a meter used to classify and drill into measurements.
 *
 * @author Jon Schneider
 */
public interface Tag {
    String getKey();

    String getValue();

    static Tag of(String key, String value) {
        return new ImmutableTag(key, value);
    }

    static Tag[] tags(String... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        Tag[] ts = new Tag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            ts[i / 2] = Tag.of(keyValues[i], keyValues[i + 1]);
        }
        return ts;
    }
}
