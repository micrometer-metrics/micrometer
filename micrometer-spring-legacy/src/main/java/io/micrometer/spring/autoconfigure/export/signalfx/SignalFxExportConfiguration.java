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
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;
import io.micrometer.signalfx.SignalFxConfig;
import io.micrometer.signalfx.SignalFxMeterRegistry;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

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

    @NonNullApi
    private class DefaultSignalFxConfig implements SignalFxConfig {
        private final SignalFxProperties props;
        private final SignalFxConfig defaults = k -> null;

        private DefaultSignalFxConfig(SignalFxProperties props) {
            this.props = props;
        }

        @Override
        @Nullable
        public String get(String k) {
            return null;
        }

        @Override
        public String accessToken() {
            return props.getAccessToken() == null ? defaults.accessToken() : props.getAccessToken();
        }

        @Override
        public String uri() {
            return props.getUri() == null ? defaults.uri() : props.getUri();
        }

        @Override
        public Duration step() {
            return props.getStep() == null ? defaults.step() : props.getStep();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public SignalFxConfig signalfxConfig(SignalFxProperties props) {
        return new DefaultSignalFxConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "management.metrics.export.Signalfx.enabled", matchIfMissing = true)
    public MetricsExporter signalFxExporter(SignalFxConfig config, Clock clock) {
        return () -> new SignalFxMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }
}
