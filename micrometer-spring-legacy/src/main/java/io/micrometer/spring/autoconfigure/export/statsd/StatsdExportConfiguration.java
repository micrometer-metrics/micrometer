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
package io.micrometer.spring.autoconfigure.export.statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to a StatsD agent.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(StatsdMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(StatsdProperties.class)
public class StatsdExportConfiguration {

    @Bean
    @ConditionalOnMissingBean(StatsdConfig.class)
    public StatsdConfig statsdConfig(StatsdProperties props) {
        return new StatsdPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnProperty(value = "management.metrics.export.statsd.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean
    public StatsdMeterRegistry statsdExporter(StatsdConfig config, HierarchicalNameMapper hierarchicalNameMapper, Clock clock) {
        return new StatsdMeterRegistry(config, hierarchicalNameMapper, clock);
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
