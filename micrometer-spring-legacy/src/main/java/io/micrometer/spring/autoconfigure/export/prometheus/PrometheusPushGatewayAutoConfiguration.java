package io.micrometer.spring.autoconfigure.export.prometheus;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.prometheus.client.exporter.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.validation.constraints.NotNull;
import java.io.IOException;

/**
 * Auto-configuration for <a href="https://github.com/prometheus/pushgateway">Prometheus Pushgateway</
 * Will activate a scheduler to push metrics to the gateway
 *
 * @author <a href="mailto:david@davidkarlsen.com">David J. M. Karlsen</a>
 */
@Configuration
@ConditionalOnClass(PushGateway.class)
@ConditionalOnMissingBean(PushGateway.class)
@ConditionalOnBean(PrometheusMeterRegistry.class)
@ConditionalOnProperty(value = "management.metrics.export.prometheus.pushgateway.enabled", havingValue = "true", matchIfMissing = true)
@ConfigurationProperties(PrometheusPushGatewayAutoConfiguration.CONFIGURATION_PREFIX)
@EnableScheduling
@AutoConfigureAfter({MetricsAutoConfiguration.class, PrometheusExportConfiguration.class})
public class PrometheusPushGatewayAutoConfiguration implements DisposableBean {
    public static final String CONFIGURATION_PREFIX = "management.metrics.export.prometheus.pushgateway";
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final PrometheusMeterRegistry prometheusMeterRegistry;

    @NotNull
    private String baseUrl;

    @NotNull
    private String job;

    private PushGateway pushGateway;

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setJob(String job) {
        this.job = job;
    }

    PrometheusPushGatewayAutoConfiguration(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @Bean
    public PushGateway pushGateway() {
        this.pushGateway = new PushGateway(baseUrl);

        return pushGateway;
    }

    /**
     * Push metrics to the gateway.
     * @throws IOException
     */
    @Scheduled(fixedRateString = "${management.metrics.export.prometheus.pushgateway.fixedRateInMs:60000}")
    public void pushMetrics() throws IOException {
        this.logger.trace("Pushing metrics");
        this.pushGateway.pushAdd(prometheusMeterRegistry.getPrometheusRegistry(), job);
    }

    /**
     * Delete metrics from Pushgateway.
     * @throws IOException
     */
    @Override
    public void destroy() throws IOException {
        if (null != this.pushGateway) {
            this.logger.info("Shutting down pushgateway");
            pushGateway.delete(job);
        }
    }

}
