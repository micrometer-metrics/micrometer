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
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test

internal class TagsKtTests {

    @Test
    fun `should return empty tags when no pairs are provided`() {
        then(tagsOf()).isSameAs(Tags.empty())
    }

    @Test
    fun `should create tags from pairs`() {
        then(tagsOf("k1" to "v1", "k2" to "v2")).containsExactly(Tag.of("k1", "v1"), Tag.of("k2", "v2"))
    }

    @Test
    fun `should deduplicate tags by key`() {
        then(tagsOf("k" to "v1", "k" to "v2")).containsExactly(Tag.of("k", "v2"))
    }
}
