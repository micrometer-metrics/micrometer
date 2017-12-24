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
package io.micrometer.spring.autoconfigure.export.jmx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.jmx.JmxConfig;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for exporting metrics to JMX.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(JmxMeterRegistry.class)
@EnableConfigurationProperties(JmxProperties.class)
public class JmxExportConfiguration {

    private class DefaultJmxConfig implements JmxConfig {
        private final JmxProperties props;

        public DefaultJmxConfig(JmxProperties props) {
            this.props = props;
        }

        @Override
        public Duration step() {
            return props.getStep() == null ? DEFAULT.step() : props.getStep();
        }

        @Override
        public String get(String k) {
            return null;
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public JmxConfig jmxConfig(JmxProperties props) {
        return new DefaultJmxConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.export.jmx.enabled", matchIfMissing = true)
    public MetricsExporter jmxExporter(JmxConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        return () -> new JmxMeterRegistry(config, nameMapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    @Bean
    @ConditionalOnMissingBean
    public HierarchicalNameMapper hierarchicalNameMapper() {
        return HierarchicalNameMapper.DEFAULT;
    }

}
