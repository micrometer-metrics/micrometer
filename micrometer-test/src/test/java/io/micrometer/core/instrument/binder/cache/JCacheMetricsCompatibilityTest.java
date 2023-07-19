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
package io.micrometer.core.instrument.binder.cache;

import org.jsr107.ri.spi.RICachingProvider;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;

import static java.util.Collections.emptyList;
import static javax.cache.expiry.Duration.ONE_HOUR;

class JCacheMetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit<Cache<String, String>> {

    @Override
    public Cache<String, String> createCache() {
        CacheManager cacheManager = new RICachingProvider().getCacheManager();

        MutableConfiguration<String, String> configuration = new MutableConfiguration<>();
        configuration.setTypes(String.class, String.class)
            .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR))
            .setStatisticsEnabled(true);

        return cacheManager.createCache("mycache", configuration);
    }

    @Override
    public CacheMeterBinder<Cache<String, String>> binder() {
        return new JCacheMetrics<>(cache, emptyList());
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
