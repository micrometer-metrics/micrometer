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
package io.micrometer.spring.autoconfigure.jdbc;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;

import io.micrometer.spring.jdbc.DataSourcePoolMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for metrics on all available
 * {@link DataSource datasources}.
 *
 * @author Stephane Nicoll
 */
@Configuration
@AutoConfigureAfter({ MetricsAutoConfiguration.class, DataSourceAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class })
@ConditionalOnClass({ DataSource.class, MeterRegistry.class })
@ConditionalOnBean({ DataSource.class, MeterRegistry.class })
public class DataSourcePoolMetricsAutoConfiguration {

    @Configuration
    @ConditionalOnBean(DataSourcePoolMetadataProvider.class)
    static class DataSourcePoolMetadataMetricsConfiguration {

        private static final String DATASOURCE_SUFFIX = "dataSource";

        private final MeterRegistry registry;

        private final Collection<DataSourcePoolMetadataProvider> metadataProviders;

        DataSourcePoolMetadataMetricsConfiguration(MeterRegistry registry,
                Collection<DataSourcePoolMetadataProvider> metadataProviders) {
            this.registry = registry;
            this.metadataProviders = metadataProviders;
        }

        @Autowired
        public void bindDataSourcesToRegistry(Map<String, DataSource> dataSources) {
            dataSources.forEach(this::bindDataSourceToRegistry);
        }

        private void bindDataSourceToRegistry(String beanName, DataSource dataSource) {
            String dataSourceName = getDataSourceName(beanName);
            new DataSourcePoolMetrics(dataSource, this.metadataProviders, dataSourceName,
                Collections.emptyList()).bindTo(this.registry);
        }

        /**
         * Get the name of a DataSource based on its {@code beanName}.
         * @param beanName the name of the data source bean
         * @return a name for the given data source
         */
        private String getDataSourceName(String beanName) {
            if (beanName.length() > DATASOURCE_SUFFIX.length()
                && StringUtils.endsWithIgnoreCase(beanName, DATASOURCE_SUFFIX)) {
                return beanName.substring(0,
                    beanName.length() - DATASOURCE_SUFFIX.length());
            }
            return beanName;
        }

    }

}
