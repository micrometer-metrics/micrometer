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

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.InvalidConfigurationException;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.management.*;
import java.util.List;

/**
 * Collect metrics on JSR-107 JCache caches, including detailed metrics on manual puts and
 * removals. See
 * https://github.com/jsr107/demo/blob/master/src/test/java/javax/cache/core/StatisticsExample.java
 * <p>
 * Note that JSR-107 does not provide any insight into the size or estimated size of the
 * cache, so the size metric of a JCache cache will always report 0.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class JCacheMetrics<K, V, C extends Cache<K, V>> extends CacheMeterBinder<C> {

    // VisibleForTesting
    @Nullable
    ObjectName objectName;

    /**
     * Record metrics on a JCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param tags Tags to apply to all recorded metrics. Must be an even number of
     * arguments representing key/value pairs of tags.
     * @param <C> The cache type.
     * @param <K> The cache key type.
     * @param <V> The cache value type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static <K, V, C extends Cache<K, V>> C monitor(MeterRegistry registry, C cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on a JCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param tags Tags to apply to all recorded metrics.
     * @param <C> The cache type.
     * @param <K> The cache key type.
     * @param <V> The cache value type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     */
    public static <K, V, C extends Cache<K, V>> C monitor(MeterRegistry registry, C cache, Iterable<Tag> tags) {
        new JCacheMetrics<>(cache, tags).bindTo(registry);
        return cache;
    }

    public JCacheMetrics(C cache, Iterable<Tag> tags) {
        super(cache, cache.getName(), tags);
        try {
            CacheManager cacheManager = cache.getCacheManager();
            if (cacheManager != null) {
                String cacheManagerUri = cacheManager.getURI().toString().replace(':', '.'); // ehcache's
                                                                                             // uri
                                                                                             // is
                                                                                             // prefixed
                                                                                             // with
                                                                                             // 'urn:'

                this.objectName = new ObjectName("javax.cache:type=CacheStatistics" + ",CacheManager=" + cacheManagerUri
                        + ",Cache=" + cache.getName());
            }
        }
        catch (MalformedObjectNameException ignored) {
            throw new InvalidConfigurationException(
                    "Cache name '" + cache.getName() + "' results in an invalid JMX name");
        }
    }

    @Override
    protected Long size() {
        // JCache statistics don't support size
        return null;
    }

    @Override
    protected long hitCount() {
        return lookupStatistic("CacheHits");
    }

    @Override
    protected Long missCount() {
        return lookupStatistic("CacheMisses");
    }

    @Override
    protected Long evictionCount() {
        return lookupStatistic("CacheEvictions");
    }

    @Override
    protected long putCount() {
        return lookupStatistic("CachePuts");
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        if (objectName != null) {
            Gauge.builder("cache.removals", objectName, objectName -> lookupStatistic("CacheRemovals"))
                .tags(getTagsWithCacheName())
                .description("Cache removals")
                .register(registry);
        }
    }

    private Long lookupStatistic(String name) {
        if (objectName != null) {
            try {
                List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
                for (MBeanServer mBeanServer : mBeanServers) {
                    try {
                        return (Long) mBeanServer.getAttribute(objectName, name);
                    }
                    catch (AttributeNotFoundException | InstanceNotFoundException ex) {
                        // did not find MBean, try the next server
                    }
                }
            }
            catch (MBeanException | ReflectionException ex) {
                throw new IllegalStateException(ex);
            }
        }

        // didn't find the MBean in any servers
        return 0L;
    }

}
