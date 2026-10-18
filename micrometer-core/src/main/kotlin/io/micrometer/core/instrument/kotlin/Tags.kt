/*
 * Copyright 2026 VMware, Inc.
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

package io.micrometer.core.instrument.kotlin

import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags

/**
 * Returns a [Tags] instance from Kotlin [Pair] values.
 *
 * @since 1.18.0
 */
fun tagsOf(vararg tags: Pair<String, String>): Tags {
    return Tags.of(tags.map { Tag.of(it.first, it.second) })
}
