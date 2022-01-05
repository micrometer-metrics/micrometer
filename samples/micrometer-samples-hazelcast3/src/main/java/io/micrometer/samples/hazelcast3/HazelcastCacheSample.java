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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;

import java.time.Duration;

public class HazelcastCacheSample {
    public static void main(String[] args) throws Exception {
        MeterRegistry registry = loggingMeterRegistry();
        IMap<String, Integer> hazelcastCache = Hazelcast.newHazelcastInstance().getMap("hazelcast.cache");
        HazelcastCacheMetrics.monitor(registry, hazelcastCache);

        for (int i = 0; i < 100; i++) {
            hazelcastCache.put("key" + i, i);
            Thread.sleep(1000);
        }
    }

    private static LoggingMeterRegistry loggingMeterRegistry() {
        return new LoggingMeterRegistry(new LoggingRegistryConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }
        }, Clock.SYSTEM);
    }

}
