/*
 * Copyright 2026 VMware, Inc.
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

package io.micrometer.core.instrument.binder.cache

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test

class CaffeineCacheMetricsKtTests {

    @Test
    fun shouldAcceptKotlinNullableCacheValueType() {
        val cache: Cache<String, FSCacheValue?> = Caffeine.newBuilder().recordStats().build()

        CaffeineCacheMetrics.monitor(SimpleMeterRegistry(), cache, "cache")
    }

    @Test
    fun shouldAcceptKotlinNullableAsyncCacheValueType() {
        val cache: AsyncCache<String, FSCacheValue?> = Caffeine.newBuilder().recordStats().buildAsync()

        CaffeineCacheMetrics.monitor(SimpleMeterRegistry(), cache, "cache")
    }

    private class FSCacheValue
}
