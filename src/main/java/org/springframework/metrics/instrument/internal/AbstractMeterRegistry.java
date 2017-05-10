/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadata;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProviders;
import org.springframework.metrics.instrument.Clock;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Tag;
import org.springframework.metrics.instrument.binder.MeterBinder;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    private Collection<Meter> meters = new ArrayList<>();
    protected Clock clock;

    @Autowired(required = false)
    private Collection<DataSourcePoolMetadataProvider> providers;

    @Autowired(required = false)
    private Collection<MeterBinder> binders;

    @PostConstruct
    private void bind() {
        for (MeterBinder binder : binders) {
            bind(binder);
        }
    }

    protected AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    protected <T extends Meter> T register(T meter) {
        meters.add(meter);
        return meter;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public Collection<Meter> getMeters() {
        return meters;
    }

    @Override
    public Cache monitor(String name, Stream<Tag> tags, Cache cache) {
        Stream<Tag> tagsWithCacheName = Streams.concat(tags, Stream.of(Tag.of("cache", name)));
        CacheStats stats = cache.stats();

        gauge("guava_cache_size", tagsWithCacheName, cache, Cache::size);
        gauge("guava_cache_hit_total", tagsWithCacheName, stats, CacheStats::hitCount);
        gauge("guava_cache_miss_total", tagsWithCacheName, stats, CacheStats::missCount);
        gauge("guava_cache_requests_total", tagsWithCacheName, stats, CacheStats::requestCount);
        gauge("guava_cache_eviction_total", tagsWithCacheName, stats, CacheStats::evictionCount);
        gauge("guava_cache_load_duration", tagsWithCacheName, stats, CacheStats::totalLoadTime);

        if(cache instanceof LoadingCache) {
            gauge("guava_cache_loads_total", tagsWithCacheName, stats, CacheStats::loadCount);
            gauge("guava_cache_load_failure_total", tagsWithCacheName, stats, CacheStats::loadExceptionCount);
        }

        return cache;
    }

    @Override
    public DataSource monitor(String name, Stream<Tag> tags, DataSource dataSource) {
        Stream<Tag> tagsWithDataSourceName = Streams.concat(tags, Stream.of(Tag.of("dataSource", name)));

        DataSourcePoolMetadataProvider provider = new DataSourcePoolMetadataProviders(providers);
        DataSourcePoolMetadata poolMetadata = provider.getDataSourcePoolMetadata(dataSource);

        if (poolMetadata != null) {
            if(poolMetadata.getActive() != null)
                gauge("data_source_active_connections", tagsWithDataSourceName, poolMetadata, DataSourcePoolMetadata::getActive);

            if(poolMetadata.getMax() != null)
                gauge("data_source_max_connections", tagsWithDataSourceName, poolMetadata, DataSourcePoolMetadata::getMax);

            if(poolMetadata.getMin() != null)
                gauge("data_source_min_connections", tagsWithDataSourceName, poolMetadata, DataSourcePoolMetadata::getMin);
        }

        return dataSource;
    }
}
