/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.autoconfigure.export.logging;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.logging.LoggingRegistryConfig;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for exporting metrics to a logging framework.
 *
 * Try SLF4J first and fallback to the standard output if it's unavailable.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnProperty(prefix = "management.metrics.export.logging", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(LoggingRegistryProperties.class)
@Import(StringToDurationConverter.class)
public class LoggingMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoggingRegistryConfig loggingRegistryConfig(LoggingRegistryProperties props) {
        return new LoggingRegistryPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoggingMeterRegistry loggingMeterRegistry(LoggingRegistryConfig config, Clock clock) {
        return new LoggingMeterRegistry(config, clock);
    }
}
