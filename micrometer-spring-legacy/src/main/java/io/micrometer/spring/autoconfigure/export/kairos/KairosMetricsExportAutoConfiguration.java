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
package io.micrometer.spring.autoconfigure.export.kairos;

import io.micrometer.core.instrument.Clock;
import io.micrometer.kairos.KairosConfig;
import io.micrometer.kairos.KairosMeterRegistry;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for exporting metrics to KairosDB.
 *
 * @author Anton Ilinchik
 * @since 1.1.0
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(KairosMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.kairos", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KairosProperties.class)
@Import(StringToDurationConverter.class)
public class KairosMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KairosConfig kairosConfig(KairosProperties props) {
        return new KairosPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public KairosMeterRegistry kairosMeterRegistry(KairosConfig config, Clock clock) {
        return new KairosMeterRegistry(config, clock);
    }
}
