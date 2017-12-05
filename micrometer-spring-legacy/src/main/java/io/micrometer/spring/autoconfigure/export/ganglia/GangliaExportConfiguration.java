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
package io.micrometer.spring.autoconfigure.export.ganglia;

import info.ganglia.gmetric4j.gmetric.GMetric;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.ganglia.GangliaConfig;
import io.micrometer.ganglia.GangliaMeterRegistry;
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
import java.util.concurrent.TimeUnit;

/**
 * Configuration for exporting metrics to Ganglia.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(GangliaMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(GangliaProperties.class)
public class GangliaExportConfiguration {

    private class DefaultGangliaConfig implements GangliaConfig {
        private final GangliaProperties props;

        private DefaultGangliaConfig(GangliaProperties props) {
            this.props = props;
        }

        @Override
        public String get(String k) {
            return null;
        }

        @Override
        public boolean enabled() {
            return props.getEnabled();
        }

        @Override
        public Duration step() {
            return props.getStep();
        }

        @Override
        public TimeUnit rateUnits() {
            return props.getRateUnits() == null ? DEFAULT.rateUnits() : props.getRateUnits();
        }

        @Override
        public TimeUnit durationUnits() {
            return props.getDurationUnits() == null ? DEFAULT.durationUnits() : props.getDurationUnits();
        }

        @Override
        public String protocolVersion() {
            return props.getProtocolVersion() == null ? DEFAULT.protocolVersion() : props.getProtocolVersion();
        }

        @Override
        public GMetric.UDPAddressingMode addressingMode() {
            return props.getAddressingMode() == null ? DEFAULT.addressingMode() : props.getAddressingMode();
        }

        @Override
        public int ttl() {
            return props.getTimeToLive() == null ? DEFAULT.ttl() : props.getTimeToLive();
        }

        @Override
        public String host() {
            return props.getHost() == null ? DEFAULT.host() : props.getHost();
        }

        @Override
        public int port() {
            return props.getPort() == null ? DEFAULT.port() : props.getPort();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public GangliaConfig gangliaConfig(GangliaProperties props) {
        return new DefaultGangliaConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.export.ganglia.enabled", matchIfMissing = true)
    public MetricsExporter gangliaExporter(GangliaConfig config,
                                           HierarchicalNameMapper nameMapper, Clock clock) {
        return () -> new GangliaMeterRegistry(config, nameMapper, clock);
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
