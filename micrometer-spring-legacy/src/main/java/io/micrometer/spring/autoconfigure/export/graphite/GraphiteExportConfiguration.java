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
package io.micrometer.spring.autoconfigure.export.graphite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to Graphite.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(GraphiteMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(GraphiteProperties.class)
public class GraphiteExportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GraphiteConfig graphiteConfig(GraphiteProperties props) {
        return new GraphitePropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnProperty(value = "management.metrics.export.graphite.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean
    public GraphiteMeterRegistry graphiteMeterRegistry(GraphiteConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        return new GraphiteMeterRegistry(config, clock, nameMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public HierarchicalNameMapper hierarchicalNameMapper() {
        return HierarchicalNameMapper.DEFAULT;
    }

}
