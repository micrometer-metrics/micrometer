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
package io.micrometer.spring.autoconfigure.export.simple;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to a {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
@Configuration
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@AutoConfigureBefore(CompositeMeterRegistryAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@EnableConfigurationProperties(SimpleProperties.class)
@ConditionalOnMissingBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.simple", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(StringToDurationConverter.class)
public class SimpleMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SimpleConfig simpleRegistryConfig(SimpleProperties props) {
        return new SimplePropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public SimpleMeterRegistry simpleMeterRegistry(SimpleConfig config, Clock clock) {
        return new SimpleMeterRegistry(config, clock);
    }
}
