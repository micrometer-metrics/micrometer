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
package io.micrometer.spring.autoconfigure.export.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Clock;
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

import java.time.Duration;

/**
 * Configuration for exporting metrics to Atlas.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 */
@Configuration
@ConditionalOnClass(AtlasMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(AtlasProperties.class)
public class AtlasExportConfiguration {

    private class DefaultAtlasConfig extends DefaultStepRegistryConfig implements AtlasConfig {
        private final AtlasProperties props;
        private final AtlasConfig defaults = k -> null;

        public DefaultAtlasConfig(AtlasProperties props) {
            super(props);
            this.props = props;
        }

        @Override
        public String uri() {
            return props.getUri() == null ? defaults.uri() : props.getUri();
        }

        @Override
        public Duration meterTTL() {
            return props.getMeterTimeToLive() == null ? defaults.meterTTL() : props.getMeterTimeToLive();
        }

        @Override
        public boolean lwcEnabled() {
            return props.getLwcEnabled() == null ? defaults.lwcEnabled() : props.getLwcEnabled();
        }

        @Override
        public Duration configRefreshFrequency() {
            return props.getConfigRefreshFrequency() == null ? defaults.configRefreshFrequency() : props.getConfigRefreshFrequency();
        }

        @Override
        public Duration configTTL() {
            return props.getConfigTimeToLive() == null ? defaults.configTTL() : props.getConfigTimeToLive();
        }

        @Override
        public String configUri() {
            return props.getConfigUri() == null ? defaults.configUri() : props.getConfigUri();
        }

        @Override
        public String evalUri() {
            return props.getEvalUri() == null ? defaults.evalUri() : props.getEvalUri();
        }
    }

    @Bean
    @ConditionalOnMissingBean(AtlasConfig.class)
    public AtlasConfig atlasConfig(AtlasProperties props) {
        return new DefaultAtlasConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.export.atlas.enabled", matchIfMissing = true)
    public MetricsExporter atlasExporter(AtlasConfig config, Clock clock) {
        return () -> new AtlasMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }
}
