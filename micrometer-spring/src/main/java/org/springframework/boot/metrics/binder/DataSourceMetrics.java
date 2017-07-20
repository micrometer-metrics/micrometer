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
package org.springframework.boot.metrics.binder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadata;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProviders;

import javax.sql.DataSource;
import java.util.Collection;

/**
 * @author Jon Schneider
 */
public class DataSourceMetrics implements MeterBinder {
    private final DataSource dataSource;
    private final Collection<DataSourcePoolMetadataProvider> metadataProviders;
    private final String name;
    private final Iterable<Tag> tags;

    public DataSourceMetrics(DataSource dataSource, Collection<DataSourcePoolMetadataProvider> metadataProviders, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.dataSource = dataSource;
        this.metadataProviders = metadataProviders;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        DataSourcePoolMetadataProvider provider = new DataSourcePoolMetadataProviders(metadataProviders);
        DataSourcePoolMetadata poolMetadata = provider.getDataSourcePoolMetadata(dataSource);

        if (poolMetadata != null) {
            if(poolMetadata.getActive() != null)
                registry.gauge(name  + "_active_connections", tags, poolMetadata, DataSourcePoolMetadata::getActive);

            if(poolMetadata.getMax() != null)
                registry.gauge(name + "_max_connections", tags, poolMetadata, DataSourcePoolMetadata::getMax);

            if(poolMetadata.getMin() != null)
                registry.gauge(name + "_min_connections", tags, poolMetadata, DataSourcePoolMetadata::getMin);
        }
    }
}
