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
package io.micrometer.spring.autoconfigure.export.influx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxConsistency;
import io.micrometer.influx.InfluxMeterRegistry;
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
 * Configuration for exporting metrics to Influx.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(InfluxMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(InfluxProperties.class)
public class InfluxExportConfiguration {

    private class DefaultInfluxConfig extends DefaultStepRegistryConfig implements InfluxConfig {
        private final InfluxProperties props;

        public DefaultInfluxConfig(InfluxProperties props) {
            super(props);
            this.props = props;
        }

        @Override
        public String db() {
            return props.getDb() == null ? DEFAULT.db() : props.getDb();
        }

        @Override
        public InfluxConsistency consistency() {
            return props.getConsistency() == null ? DEFAULT.consistency() : props.getConsistency();
        }

        @Override
        public String userName() {
            return props.getUserName() == null ? DEFAULT.userName() : props.getUserName();
        }

        @Override
        public String password() {
            return props.getPassword() == null ? DEFAULT.password() : props.getPassword();
        }

        @Override
        public String retentionPolicy() {
            return props.getRetentionPolicy() == null ? DEFAULT.retentionPolicy() : props.getRetentionPolicy();
        }

        @Override
        public String uri() {
            return props.getUri() == null ? DEFAULT.uri() : props.getUri();
        }

        @Override
        public boolean compressed() {
            return props.getCompressed() == null ? DEFAULT.compressed() : props.getCompressed();
        }

        @Override
        public Duration timerPercentilesMax() {
            return props.getTimerPercentilesMax();
        }

        @Override
        public Duration timerPercentilesMin() {
            return props.getTimerPercentilesMin();
        }
    }

    @Bean
    @ConditionalOnMissingBean(InfluxConfig.class)
    public InfluxConfig influxConfig(InfluxProperties props) {
        return new DefaultInfluxConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.influx.enabled", matchIfMissing = true)
    public MetricsExporter influxExporter(InfluxConfig config, Clock clock) {
        return () -> new InfluxMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

}
