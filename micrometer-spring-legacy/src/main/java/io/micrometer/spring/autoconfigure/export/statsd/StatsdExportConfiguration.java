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
        private final StatsdConfig defaults = k -> null;

        private DefaultStatsdConfig(StatsdProperties props) {
            this.props = props;
        }

        @Override
        public String get(String k) {
            return null;
        }

        @Override
        public StatsdFlavor flavor() {
            return props.getFlavor() == null ? defaults.flavor() : props.getFlavor();
        }

        @Override
        public boolean enabled() {
            return props.getEnabled();
        }

        @Override
        public String host() {
            return props.getHost() == null ? defaults.host() : props.getHost();
        }

        @Override
        public int port() {
            return props.getPort() == null ? defaults.port() : props.getPort();
        }

        @Override
        public int maxPacketLength() {
            return props.getMaxPacketLength() == null ? defaults.maxPacketLength() : props.getMaxPacketLength();
        }

        @Override
        public Duration pollingFrequency() {
            return props.getPollingFrequency() == null ? defaults.pollingFrequency() : props.getPollingFrequency();
        }

        @Override
        public int queueSize() {
            return props.getQueueSize() == null ? defaults.queueSize() : props.getQueueSize();
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
    public Clock clock() {
        return Clock.SYSTEM;
    }
}
