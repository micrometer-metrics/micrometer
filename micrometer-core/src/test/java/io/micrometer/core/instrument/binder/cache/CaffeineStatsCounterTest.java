/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.instrument.binder.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CaffeineStatsCounter}.
 *
 * @author John Karp
 */
class CaffeineStatsCounterTest {

    private static final String CACHE_NAME = "foo";

    private static final Tags USER_TAGS = Tags.of("k", "v");

    private static final Tags TAGS = Tags.concat(USER_TAGS, "cache", CACHE_NAME);

    private CaffeineStatsCounter stats;

    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        stats = new CaffeineStatsCounter(registry, CACHE_NAME, USER_TAGS);
    }

    @Test
    void registerSize() {
        Cache<String, String> cache = Caffeine.newBuilder().maximumSize(10).recordStats(() -> stats).build();
        stats.registerSizeMetric(cache);
        assertThat(fetch("cache.size").gauge().value()).isEqualTo(0);
        cache.put("foo", "bar");
        assertThat(fetch("cache.size").gauge().value()).isEqualTo(1);
    }

    @Test
    void hit() {
        stats.recordHits(2);
        assertThat(fetch("cache.gets", "result", "hit").counter().count()).isEqualTo(2);
    }

    @Test
    void miss() {
        stats.recordMisses(2);
        assertThat(fetch("cache.gets", "result", "miss").counter().count()).isEqualTo(2);
    }

    @Test
    void loadSuccess() {
        stats.recordLoadSuccess(256);
        Timer timer = fetch("cache.loads", "result", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(256);
    }

    @Test
    void loadFailure() {
        stats.recordLoadFailure(256);
        Timer timer = fetch("cache.loads", "result", "failure").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(256);
    }

    @ParameterizedTest
    @EnumSource
    void evictionWithCause(RemovalCause cause) {
        stats.recordEviction(3, cause);
        DistributionSummary summary = fetch("cache.evictions", "cause", cause.name()).summary();
        assertThat(summary.count()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualTo(3);
    }

    private RequiredSearch fetch(String name, String... tags) {
        return registry.get(name).tags(TAGS).tags(tags);
    }

}
