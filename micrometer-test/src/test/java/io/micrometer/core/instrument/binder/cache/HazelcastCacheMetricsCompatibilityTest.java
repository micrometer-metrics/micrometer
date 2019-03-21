/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.cache;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.jupiter.api.AfterEach;

import static java.util.Collections.emptyList;

public class HazelcastCacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit {
    private HazelcastInstance hazelcast;
    private IMap<String, String> cache;

    HazelcastCacheMetricsCompatibilityTest() {
        Config config = new Config();
        this.hazelcast = Hazelcast.newHazelcastInstance(config);
        this.cache = hazelcast.getMap("mycache");
    }

    @AfterEach
    void cleanup() {
        hazelcast.shutdown();
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
