package io.micrometer.spring.autoconfigure.export.azure;

import io.micrometer.azure.AzureConfig;
import io.micrometer.azure.AzureMeterRegistry;
import io.micrometer.core.instrument.Clock;
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

@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(AzureMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.azure.application-insights", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AzureProperties.class)
@Import(StringToDurationConverter.class)
public class AzureMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AzureConfig azureConfig(AzureProperties properties) {
        return new AzurePropertiesConfigAdapter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AzureMeterRegistry azureMeterRegistry(AzureConfig config, Clock clock) {
        return new AzureMeterRegistry(config, clock);
    }
}
