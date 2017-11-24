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
import io.micrometer.newrelic.NewRelicConfig;
import io.micrometer.newrelic.NewRelicMeterRegistry;
import io.micrometer.spring.autoconfigure.export.DefaultStepRegistryConfig;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.newrelic.NewRelicProperties;
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
@ConditionalOnClass(NewRelicMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(NewRelicProperties.class)
public class NewRelicExportConfiguration {

    private class DefaultNewRelicConfig extends DefaultStepRegistryConfig implements NewRelicConfig {
        private final NewRelicProperties props;
        private final NewRelicConfig defaults = k -> null;

        private DefaultNewRelicConfig(NewRelicProperties props) {
            super(props);
            this.props = props;
        }

        @Override
        public String apiKey() {
            return props.getApiKey() == null ? defaults.apiKey() : props.getApiKey();
        }

        @Override
        public String accountId() {
            return props.getAccountId() == null ? defaults.accountId() : props.getAccountId();
        }

        @Override
        public String uri() {
            return props.getUri() == null ? defaults.uri() : props.getUri();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public NewRelicConfig newRelicConfig(NewRelicProperties props) {
        return new DefaultNewRelicConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.newrelic.enabled", matchIfMissing = true)
    public MetricsExporter newRelicExporter(NewRelicConfig config, Clock clock) {
        return () -> new NewRelicMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

}
