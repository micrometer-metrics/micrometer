/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.export.influx2;

import io.micrometer.core.instrument.Clock;
import io.micrometer.influx2.Influx2Config;
import io.micrometer.influx2.Influx2MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to Influx.
 *
 * @author Jakub Bednar
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(Influx2MeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.influx2", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Influx2Properties.class)
@Import(StringToDurationConverter.class)
public class Influx2MetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InfluxConfig.class)
    public Influx2Config influxConfig(Influx2Properties props) {
        return new Influx2PropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public Influx2MeterRegistry influxMeterRegistry(Influx2Config config, Clock clock) {
        return new Influx2MeterRegistry(config, clock);
    }
}
