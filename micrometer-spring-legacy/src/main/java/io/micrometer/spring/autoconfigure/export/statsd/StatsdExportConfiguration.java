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
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

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

    private class DefaultStatsdConfig implements StatsdConfig {
        private final StatsdProperties props;

        private DefaultStatsdConfig(StatsdProperties props) {
            this.props = props;
        }

        @Override
        public String get(String k) {
            return null;
        }

        @Override
        public StatsdFlavor flavor() {
            return props.getFlavor() == null ? DEFAULT.flavor() : props.getFlavor();
        }

        @Override
        public boolean enabled() {
            return props.getEnabled();
        }

        @Override
        public String host() {
            return props.getHost() == null ? DEFAULT.host() : props.getHost();
        }

        @Override
        public int port() {
            return props.getPort() == null ? DEFAULT.port() : props.getPort();
        }

        @Override
        public int maxPacketLength() {
            return props.getMaxPacketLength() == null ? DEFAULT.maxPacketLength() : props.getMaxPacketLength();
        }

        @Override
        public Duration pollingFrequency() {
            return props.getPollingFrequency() == null ? DEFAULT.pollingFrequency() : props.getPollingFrequency();
        }

        @Override
        public int queueSize() {
            return props.getQueueSize() == null ? DEFAULT.queueSize() : props.getQueueSize();
        }
    }

    @Bean
    @ConditionalOnMissingBean(StatsdConfig.class)
    public StatsdConfig statsdConfig(StatsdProperties props) {
        return new DefaultStatsdConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.statsd.enabled", matchIfMissing = true)
    public MetricsExporter statsdExporter(StatsdConfig config, Clock clock) {
        return () -> new StatsdMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }
}
