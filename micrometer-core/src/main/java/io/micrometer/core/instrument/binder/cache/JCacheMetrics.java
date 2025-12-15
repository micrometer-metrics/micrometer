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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

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
public class JCacheMetrics<K, V, C extends Cache<K, V>> extends CacheMeterBinder<C> {

    // VisibleForTesting
    @Nullable ObjectName objectName;

    private final boolean registerCacheRemovalsAsFunctionCounter;

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
        return monitor(registry, cache, Tags.of(tags), false);
    }

    /**
     * Record metrics on a JCache cache.
     * @param registry The registry to bind metrics to.
     * @param cache The cache to instrument.
     * @param tags Tags to apply to all recorded metrics.
     * @param registerCacheRemovalsAsFunctionCounter whether to register the
     * {@code cache.removals} metric as a FunctionCounter
     * @param <C> The cache type.
     * @param <K> The cache key type.
     * @param <V> The cache value type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or
     * proxied in any way.
     * @since 1.14.9
     */
    public static <K, V, C extends Cache<K, V>> C monitor(MeterRegistry registry, C cache, Iterable<Tag> tags,
            boolean registerCacheRemovalsAsFunctionCounter) {
        new JCacheMetrics<>(cache, tags, registerCacheRemovalsAsFunctionCounter).bindTo(registry);
        return cache;
    }

    public JCacheMetrics(C cache, Iterable<Tag> tags) {
        this(cache, tags, true);
    }

    /**
     * Create a {@link CacheMeterBinder} for a JCache instance.
     * @param cache the JCache instance to instrument
     * @param tags additional tags to add to JCache meters
     * @param registerCacheRemovalsAsFunctionCounter whether to register the
     * {@code cache.removals} metric as a FunctionCounter
     * @since 1.14.9
     */
    public JCacheMetrics(C cache, Iterable<Tag> tags, boolean registerCacheRemovalsAsFunctionCounter) {
        super(cache, cache.getName(), tags);
        this.registerCacheRemovalsAsFunctionCounter = registerCacheRemovalsAsFunctionCounter;
        try {
            CacheManager cacheManager = cache.getCacheManager();
            if (cacheManager != null) {
                // ehcache's uri is prefixed with 'urn:'
                String cacheManagerUri = cacheManager.getURI().toString().replace(':', '.');

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
    protected @Nullable Long size() {
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
            if (registerCacheRemovalsAsFunctionCounter) {
                FunctionCounter.builder("cache.removals", objectName, objectName -> lookupStatistic("CacheRemovals"))
                    .tags(getTagsWithCacheName())
                    .description("Cache removals")
                    .register(registry);
            }
            else {
                Gauge.builder("cache.removals", objectName, objectName -> lookupStatistic("CacheRemovals"))
                    .tags(getTagsWithCacheName())
                    .description("Cache removals")
                    .register(registry);
            }
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
