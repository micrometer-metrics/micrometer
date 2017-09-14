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
package io.micrometer.spring.jdbc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadata;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProviders;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Jon Schneider
 */
public class DataSourceMetrics implements MeterBinder {
    private final String name;
    private final Iterable<Tag> tags;
    private final DataSourcePoolMetadata poolMetadata;

    // prevents the poolMetadata that we base the gauges on from being garbage collected
    private static Collection<DataSourcePoolMetadata> instrumentedPools = new ArrayList<>();

    public DataSourceMetrics(DataSource dataSource, Collection<DataSourcePoolMetadataProvider> metadataProviders, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;

        DataSourcePoolMetadataProvider provider = new DataSourcePoolMetadataProviders(metadataProviders);
        poolMetadata = provider.getDataSourcePoolMetadata(dataSource);
        instrumentedPools.add(poolMetadata);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (poolMetadata != null) {
            if (poolMetadata.getActive() != null)
                registry.gauge(name + ".active.connections", tags, poolMetadata, DataSourcePoolMetadata::getActive);

            if (poolMetadata.getMax() != null)
                registry.gauge(name + ".max.connections", tags, poolMetadata, DataSourcePoolMetadata::getMax);

            if (poolMetadata.getMin() != null)
                registry.gauge(name + ".min.connections", tags, poolMetadata, DataSourcePoolMetadata::getMin);
        }
    }
}
