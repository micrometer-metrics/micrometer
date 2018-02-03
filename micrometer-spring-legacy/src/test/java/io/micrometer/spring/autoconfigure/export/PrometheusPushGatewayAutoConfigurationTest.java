package io.micrometer.spring.autoconfigure.export;

import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusExportConfiguration;
import io.micrometer.spring.autoconfigure.export.prometheus.PrometheusPushGatewayAutoConfiguration;
import io.prometheus.client.exporter.PushGateway;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
    PrometheusExportConfiguration.class,
    MetricsAutoConfiguration.class,
    PrometheusPushGatewayAutoConfiguration.class
}, properties = {
    "debug=true",
    "management.metrics.export.prometheus.enabled=true",
    "management.metrics.export.prometheus.pushgateway.baseUrl=localhost:9091",
    "management.metrics.export.prometheus.pushgateway.job=myjob"
})
public class PrometheusPushGatewayAutoConfigurationTest {

    @Inject
    private PushGateway pushGateway;

    @Test
    public void testThatPushGatewayIsCreated() {
        Assertions.assertThat(pushGateway)
            .isNotNull()
            .extracting("gatewayBaseURL").containsExactly("http://localhost:9091/metrics/job/");
    }
}
