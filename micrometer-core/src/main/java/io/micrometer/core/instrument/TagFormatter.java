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
package io.micrometer.core.instrument;

/**
 * Many metrics backends have constraints on valid characters that may appear
 * in a tag key/value or metric name. While it is recommended to choose tag
 * keys/values that are absent special characters that are invalid on any
 * common metrics backend, sometimes this is hard to avoid (as in the format
 * of the URI template for parameterized URIs like /api/person/{id} emanating
 * from Spring Web).
 *
 * @author Jon Schneider
 */
public interface TagFormatter {
    TagFormatter identity = new TagFormatter() {};

    default String formatName(String name) { return name; }
    default String formatTagKey(String key) { return key; }
    default String formatTagValue(String value) { return value; }
}
