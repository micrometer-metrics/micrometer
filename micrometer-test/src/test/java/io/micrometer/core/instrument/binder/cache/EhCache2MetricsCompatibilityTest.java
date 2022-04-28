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

import io.micrometer.common.lang.Nullable;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import org.junit.jupiter.api.AfterEach;

import java.util.UUID;

import static java.util.Collections.emptyList;

class EhCache2MetricsCompatibilityTest extends CacheMeterBinderCompatibilityKit<Ehcache> {

    private CacheManager cacheManager;

    @AfterEach
    void after() {
        cacheManager.removeAllCaches();
    }

    @Override
    public void dereferenceCache() {
        super.dereferenceCache();
        this.cacheManager.removeAllCaches();
    }

    @Override
    public Cache createCache() {
        Configuration config = ConfigurationFactory.parseConfiguration();
        config.setName(UUID.randomUUID().toString());

        this.cacheManager = CacheManager.newInstance(config);
        this.cacheManager.addCache("mycache");
        return cacheManager.getCache("mycache");
    }

    @Override
    public CacheMeterBinder<Ehcache> binder() {
        return new EhCache2Metrics(cache, emptyList());
    }

    @Override
    public void put(String key, String value) {
        cache.put(new Element(key, value, 1));
    }

    @Nullable
    @Override
    public String get(String key) {
        Element element = cache.get(key);
        return element == null ? null : (String) element.getObjectValue();
    }

}
