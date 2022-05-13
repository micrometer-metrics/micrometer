/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.samples.hazelcast3;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinderCompatibilityKit;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static java.util.Collections.emptyList;

class Hazelcast3CacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit<Object> {

    private Config config = new Config("hazelcast3-cache-test");

    private IMap<String, String> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        this.cache = (IMap<String, String>) super.cache;
    }

    @AfterEach
    void cleanup() {
        Hazelcast.shutdownAll();
    }

    @Override
    public void dereferenceCache() {
        super.dereferenceCache();
        Hazelcast.getOrCreateHazelcastInstance(config).getMap("mycache").destroy();
        this.cache = null;
    }

    @Override
    public IMap<String, String> createCache() {
        return Hazelcast.newHazelcastInstance(config).getMap("mycache");
    }

    @Override
    public CacheMeterBinder<Object> binder() {
        return new HazelcastCacheMetrics(super.cache, emptyList());
    }

    @Override
    public void put(String key, String value) {
        this.cache.put(key, value);
    }

    @Override
    public String get(String key) {
        return this.cache.get(key);
    }

}
