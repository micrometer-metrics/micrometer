/*
 * Copyright 2013-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
    fun `should create tags from pairs`() {
        val tags = tagsOf("method" to "GET", "status" to "200")

        then(tags).containsExactly(Tag.of("method", "GET"), Tag.of("status", "200"))
    }

    @Test
    fun `should return empty tags when pairs are empty`() {
        val tags = tagsOf()

        then(tags).isSameAs(Tags.empty())
    }

    @Test
    fun `should retain last pair when keys are duplicated`() {
        val tags = tagsOf("status" to "200", "status" to "201")

        then(tags).containsExactly(Tag.of("status", "201"))
    }
}
