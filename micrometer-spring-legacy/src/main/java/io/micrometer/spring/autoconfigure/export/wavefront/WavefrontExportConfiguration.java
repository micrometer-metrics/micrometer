package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.core.instrument.Clock;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.wavefront.WavefrontConfig;
import io.micrometer.wavefront.WavefrontMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnClass(WavefrontMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(WavefrontProperties.class)
public class WavefrontExportConfiguration {

    @Bean
    @ConditionalOnMissingBean(WavefrontConfig.class)
    public WavefrontConfig wavefrontConfig(WavefrontProperties props) {
        return new WavefrontPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnProperty(value = "management.metrics.export.wavefront.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean
    public WavefrontMeterRegistry wavefrontMeterRegistry(WavefrontConfig config, Clock clock) {
        return new WavefrontMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }
}
