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
package io.micrometer.core.samples;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.GuavaCacheMetrics;
import io.micrometer.core.samples.utils.SampleRegistries;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static java.util.Collections.emptyList;

public class CacheSample {
    public static void main(String[] args) {
        LoadingCache<Integer, Integer> cache = CacheBuilder.newBuilder()
                .maximumSize(10)
                .recordStats()
                .build(new CacheLoader<Integer, Integer>() {
                    @Override
                    public Integer load(Integer key) {
                        Mono.delay(Duration.ofMillis(100)).block();
                        return -1*key;
                    }
                });

        MeterRegistry registry = SampleRegistries.prometheus();
        new GuavaCacheMetrics("inverting.cache", emptyList(), cache).bindTo(registry);

        for(int i = 0;; i++) {
            cache.getUnchecked(i);
        }
    }
}
