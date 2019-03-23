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
package io.micrometer.spring.autoconfigure.export.stackdriver;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.monitoring.v3.MetricServiceSettings;
import io.micrometer.core.instrument.Clock;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import io.micrometer.stackdriver.StackdriverConfig;
import io.micrometer.stackdriver.StackdriverMeterRegistry;
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
import org.springframework.core.io.Resource;

/**
 * Configuration for exporting metrics to Stackdriver.
 *
 * @author Jon Schneider
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(StackdriverMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.stackdriver", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(StackdriverProperties.class)
@Import(StringToDurationConverter.class)
public class StackdriverMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StackdriverConfig stackdriverConfig(StackdriverProperties props) {
        return new StackdriverPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public StackdriverMeterRegistry stackdriverMeterRegistry(StackdriverConfig config, StackdriverProperties props, Clock clock) {
        return StackdriverMeterRegistry.builder(config)
                .clock(clock)
                .metricServiceSettings(() -> {
                    MetricServiceSettings.Builder settingsBuilder = MetricServiceSettings.newBuilder();
                    Resource credentials = props.getServiceAccountCredentials();
                    if (credentials != null) {
                        settingsBuilder.setCredentialsProvider(
                                FixedCredentialsProvider.create(ServiceAccountCredentials.fromStream(credentials.getInputStream())));
                    }
                    return settingsBuilder.build();
                })
                .build();
    }
}
