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
package io.micrometer.spring.cache;

import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinderCompatibilityKit;
import io.micrometer.core.lang.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static java.util.Collections.emptyList;

public class ConcurrentMapCacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit {
    @Nullable
    private ConcurrentMapCache cache;

    @Override
    public CacheMeterBinder binder() {
        ConcurrentMapCacheMetrics binder = new ConcurrentMapCacheMetrics(new ConcurrentMapCache("mycache"), emptyList());
        this.cache = binder.getMonitoredCache();
        return binder;
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
    }

    @Override
    public String get(String key) {
        Cache.ValueWrapper valueWrapper = cache.get(key);
        return valueWrapper == null ? null : (String) valueWrapper.get();
    }
}
