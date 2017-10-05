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
import io.micrometer.graphite.GraphiteProtocol;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    private class DefaultGraphiteConfig implements GraphiteConfig {
        private final GraphiteProperties props;
        private final GraphiteConfig defaults = k -> null;

        public DefaultGraphiteConfig(GraphiteProperties props) {
            this.props = props;
        }

        @Override
        public String get(String k) {
            return null;
        }

        @Override
        public boolean enabled() {
            return props.getEnabled();
        }

        @Override
        public Duration step() {
            return props.getStep();
        }

        @Override
        public TimeUnit rateUnits() {
            return props.getRateUnits() == null ? defaults.rateUnits() : props.getRateUnits();
        }

        @Override
        public TimeUnit durationUnits() {
            return props.getDurationUnits() == null ? defaults.durationUnits() : props.getDurationUnits();
        }

        @Override
        public String host() {
            return props.getHost() == null ? defaults.host() : props.getHost();
        }

        @Override
        public int port() {
            return props.getPort() == null ? defaults.port() : props.getPort();
        }

        @Override
        public GraphiteProtocol protocol() {
            return props.getProtocol() == null ? defaults.protocol() : props.getProtocol();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public GraphiteConfig graphiteConfig(GraphiteProperties props) {
        return new DefaultGraphiteConfig(props);
    }
    
    @Bean
    @ConditionalOnProperty(value = "spring.metrics.graphite.enabled", matchIfMissing = true)
    public MetricsExporter graphiteExporter(GraphiteConfig config,
                                            HierarchicalNameMapper nameMapper, Clock clock) {
        return () -> new GraphiteMeterRegistry(config, nameMapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.SYSTEM;
    }

    @Bean
    @ConditionalOnMissingBean
    public HierarchicalNameMapper hierarchicalNameMapper() {
        return HierarchicalNameMapper.DEFAULT;
    }

}
