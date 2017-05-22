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
import org.springframework.core.env.Environment;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.binder.MeterBinder;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    protected Clock clock;

    @Autowired(required = false)
    private Collection<DataSourcePoolMetadataProvider> providers;

    @Autowired(required = false)
    private Collection<MeterBinder> binders;

    @SuppressWarnings("ConstantConditions")
    @PostConstruct
    private void bind() {
        if(binders != null) {
            for (MeterBinder binder : binders) {
                bind(binder);
            }
        }
    }

    protected AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock getClock() {
        return clock;
    }

    @Override
    public DataSource monitor(String name, Stream<Tag> tags, DataSource dataSource) {
        DataSourcePoolMetadataProvider provider = new DataSourcePoolMetadataProviders(providers);
        DataSourcePoolMetadata poolMetadata = provider.getDataSourcePoolMetadata(dataSource);

        if (poolMetadata != null) {
            if(poolMetadata.getActive() != null)
                gauge(name  + "_active_connections", tags, poolMetadata, DataSourcePoolMetadata::getActive);

            if(poolMetadata.getMax() != null)
                gauge(name + "_max_connections", tags, poolMetadata, DataSourcePoolMetadata::getMax);

            if(poolMetadata.getMin() != null)
                gauge(name + "_min_connections", tags, poolMetadata, DataSourcePoolMetadata::getMin);
        }

        return dataSource;
    }
}
