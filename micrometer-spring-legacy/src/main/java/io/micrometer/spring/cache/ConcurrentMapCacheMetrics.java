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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.cache.CacheMeterBinder;
import io.micrometer.core.lang.Nullable;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Jon Schneider
 */
public class ConcurrentMapCacheMetrics extends CacheMeterBinder {
    private final MonitoredConcurrentMapCache cache;

    /**
     * Record metrics on a ConcurrentMapCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static ConcurrentMapCache monitor(MeterRegistry registry, ConcurrentMapCache cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on a ConcurrentMapCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static ConcurrentMapCache monitor(MeterRegistry registry, ConcurrentMapCache cache, Iterable<Tag> tags) {
        new ConcurrentMapCacheMetrics(cache, tags).bindTo(registry);
        return cache;
    }

    public ConcurrentMapCacheMetrics(ConcurrentMapCache cache, Iterable<Tag> tags) {
        super(cache, cache.getName(), tags);
        this.cache = new MonitoredConcurrentMapCache(cache);
    }

    /**
     * @return A {@link ConcurrentMapCache} wrapper that collects metrics on its use.
     */
    public MonitoredConcurrentMapCache getMonitoredCache() {
        return cache;
    }

    @Override
    protected Long size() {
        return (long) cache.getNativeCache().size();
    }

    @Override
    protected long hitCount() {
        return cache.hitCount.get();
    }

    @Override
    protected Long missCount() {
        return cache.missCount.get();
    }

    @Override
    protected Long evictionCount() {
        return cache.evictCount.get();
    }

    @Override
    protected long putCount() {
        return cache.putCount.get();
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
    }

    /**
     * A {@link ConcurrentMapCache} wrapper that collects metrics on its use.
     */
    public static class MonitoredConcurrentMapCache extends ConcurrentMapCache {
        private AtomicLong hitCount = new AtomicLong(0);
        private AtomicLong missCount = new AtomicLong(0);
        private AtomicLong putCount = new AtomicLong(0);
        private AtomicLong evictCount = new AtomicLong(0);

        private ConcurrentMapCache delegate;

        MonitoredConcurrentMapCache(ConcurrentMapCache delegate) {
            super(delegate.getName(), delegate.getNativeCache(), delegate.isAllowNullValues());
            this.delegate = delegate;
        }

        @Override
        public ValueWrapper get(Object key) {
            countGet(key);
            return delegate.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            countGet(key);
            return delegate.get(key, type);
        }

        @Nullable
        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            countGet(key);
            return delegate.get(key, valueLoader);
        }

        @Nullable
        private ValueWrapper countGet(Object key) {
            ValueWrapper valueWrapper = delegate.get(key);
            if (valueWrapper != null)
                hitCount.incrementAndGet();
            else
                missCount.incrementAndGet();
            return valueWrapper;
        }

        @Override
        public void put(Object key, Object value) {
            putCount.incrementAndGet();
            delegate.put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            if (!getNativeCache().containsKey(key)) {
                // no need to synchronize this with the subsequent putIfAbsent, as put count is
                // OK to be an approximation.
                putCount.incrementAndGet();
            }
            return delegate.putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            evictCount.incrementAndGet();
            delegate.evict(key);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }
}
