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

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.map.IMap;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinderCompatibilityKit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class HazelcastCacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit<Object> {
    private Config config = new Config();
    private IMap<String, String> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        this.cache = (IMap<String, String>) super.cache;
    }

    @Override
    public void dereferenceCache() {
        super.dereferenceCache();
        this.cache.destroy();
        this.cache = null;
    }

    @Disabled("This only demonstrates why we can't support miss count in Hazelcast.")
    @Issue("#586")
    @Test
    void multiInstanceMissCount() {
        IMap<String, String> cache2 = Hazelcast.newHazelcastInstance(config).getMap("mycache");

        // Since each member owns 1/N (N being the number of members in the cluster) entries of a distributed map,
        // we add two entries so we can deterministically say that each cache will "own" one entry.
        cache.put("k1", "v");
        cache.put("k2", "v");

        cache.get("k1");
        cache.get("k2");

        // cache stats: hits = 1, gets = 2, puts = 2
        // cache2 stats: hits = 1, gets = 0, puts = 0

        assertThat(cache.getLocalMapStats().getHits()).isEqualTo(1);
        assertThat(cache.getLocalMapStats().getGetOperationCount()).isEqualTo(2);
        assertThat(cache2.getLocalMapStats().getHits()).isEqualTo(1);

        // ... and this is why we can't calculate miss count in Hazelcast. sorry!
    }

    @AfterEach
    void cleanup() {
        Hazelcast.shutdownAll();
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
