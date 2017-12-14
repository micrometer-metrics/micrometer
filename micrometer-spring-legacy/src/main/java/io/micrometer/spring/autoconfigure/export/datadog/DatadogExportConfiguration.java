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
package io.micrometer.spring.autoconfigure.export.datadog;

import io.micrometer.core.instrument.Clock;
import io.micrometer.datadog.DatadogConfig;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.spring.autoconfigure.export.DefaultStepRegistryConfig;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to Datadog.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(DatadogMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(DatadogProperties.class)
public class DatadogExportConfiguration {

    private class DefaultDatadogConfig extends DefaultStepRegistryConfig implements DatadogConfig {
        private final DatadogProperties props;
        private final DatadogConfig defaults = k -> null;

        private DefaultDatadogConfig(DatadogProperties props) {
            super(props);
            this.props = props;
        }

        @Override
        public String apiKey() {
            return props.getApiKey();
        }

        @Override
        public String hostTag() {
            return props.getHostKey() == null ? defaults.hostTag() : props.getHostKey();
        }

        @Override
        public boolean descriptions() {
            return props.getDescriptions() == null ? defaults.descriptions() : props.getDescriptions();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public DatadogConfig datadogConfig(DatadogProperties props) {
        return new DefaultDatadogConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.export.datadog.enabled", matchIfMissing = true)
    public MetricsExporter datadogExporter(DatadogConfig config, Clock clock) {
        return () -> new DatadogMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

}
