/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.samples.hazelcast3;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinderCompatibilityKit;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import org.junit.jupiter.api.AfterEach;

import static java.util.Collections.emptyList;

class Hazelcast3CacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit {
    private IMap<String, String> cache = Hazelcast.newHazelcastInstance().getMap("mycache");

    @AfterEach
    void cleanup() {
        Hazelcast.shutdownAll();
    }

    @Override
    public CacheMeterBinder binder() {
        return new HazelcastCacheMetrics(cache, emptyList());
    }

    @Override
    public void put(String key, String value) {
        cache.put(key, value);
    }

    @Override
    public String get(String key) {
        return cache.get(key);
    }
}
