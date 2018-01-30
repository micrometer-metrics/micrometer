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
package io.micrometer.spring.autoconfigure.export.signalfx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.signalfx.SignalFxConfig;
import io.micrometer.signalfx.SignalFxMeterRegistry;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to Signalfx.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(SignalFxMeterRegistry.class)
@EnableConfigurationProperties(SignalFxProperties.class)
@Import(StringToDurationConverter.class)
public class SignalFxExportConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SignalFxConfig signalfxConfig(SignalFxProperties props) {
        return new SignalFxPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnProperty(value = "management.metrics.export.signalfx.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SignalFxMeterRegistry signalFxMeterRegistry(SignalFxConfig config, Clock clock) {
        return new SignalFxMeterRegistry(config, clock);
    }
}
