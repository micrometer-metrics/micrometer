/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.binder.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinderCompatibilityKit;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;

class CaffeineCacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit<LoadingCache<String, String>> {
    private AtomicReference<String> loadValue = new AtomicReference<>();

    @Override
    public LoadingCache<String, String> createCache() {
        return Caffeine.newBuilder()
                .maximumSize(2)
                .recordStats()
                .executor(Runnable::run)
                .build(key -> {
                    String val = loadValue.getAndSet(null);
                    if (val == null)
                        throw new Exception("don't load this key");
                    return val;
                });
    }

    @Override
    public CacheMeterBinder<LoadingCache<String, String>> binder() {
        return new CaffeineCacheMetrics<>(cache, "mycache", emptyList());
    }

    @Override
    public void put(String key, String value) {
        synchronized (this) {
            loadValue.set(value);
            cache.get(key);
        }
    }

    @Override
    public String get(String key) {
        try {
            return cache.get(key);
        } catch (Exception ignored) {
            return null;
        }
    }
}
