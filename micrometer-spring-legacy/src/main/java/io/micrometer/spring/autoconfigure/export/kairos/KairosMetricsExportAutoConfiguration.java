package io.micrometer.spring.autoconfigure.export.kairos;

import io.micrometer.core.instrument.Clock;
import io.micrometer.kairos.KairosConfig;
import io.micrometer.kairos.KairosMeterRegistry;
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
 * Configuration for exporting metrics to Kairos.
 *
 * @author Anton Ilinchik
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(KairosMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.kairos", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KairosProperties.class)
@Import(StringToDurationConverter.class)
public class KairosMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KairosConfig kairosConfig(KairosProperties props) {
        return new KairosPropertiesConfigAdapter(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public KairosMeterRegistry kairosMeterRegistry(KairosConfig config, Clock clock) {
        return new KairosMeterRegistry(config, clock);
    }
}
