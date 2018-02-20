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
package io.micrometer.spring.jdbc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadata;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProviders;

import javax.sql.DataSource;
import java.util.Collection;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class DataSourcePoolMetrics implements MeterBinder {
    private final DataSource dataSource;
    private final String name;
    private final Iterable<Tag> tags;

    @Nullable
    private final DataSourcePoolMetadata poolMetadata;

    public DataSourcePoolMetrics(DataSource dataSource, @Nullable Collection<DataSourcePoolMetadataProvider> metadataProviders, String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = tags;
        this.dataSource = dataSource;
        DataSourcePoolMetadataProvider provider = new DataSourcePoolMetadataProviders(metadataProviders);
        this.poolMetadata = provider.getDataSourcePoolMetadata(dataSource);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (poolMetadata != null) {
            registry.gauge(name + ".connections.active", tags, dataSource, dataSource -> poolMetadata.getActive() != null ? poolMetadata.getActive() : 0);
            registry.gauge(name + ".connections.max", tags, dataSource, dataSource -> poolMetadata.getMax());
            registry.gauge(name + ".connections.min", tags, dataSource, dataSource -> poolMetadata.getMin());
        }
    }
}
