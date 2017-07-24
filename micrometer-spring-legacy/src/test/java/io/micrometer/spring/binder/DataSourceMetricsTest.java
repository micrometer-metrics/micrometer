package io.micrometer.spring.binder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.spring.SpringMeters;
import io.micrometer.spring.export.prometheus.EnablePrometheusMetrics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collection;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "spring.datasource.generate-unique-name=true", "management.security.enabled=false" })
public class DataSourceMetricsTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void dataSourceIsInstrumented() throws SQLException, InterruptedException {
        dataSource.getConnection().getMetaData();
        String scrape = restTemplate.getForObject("/prometheus", String.class);
        System.out.println(scrape);
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    @EnablePrometheusMetrics
    @Import(DataSourceConfig.class)
    static class MetricsApp {
        public static void main(String[] args) {
            SpringApplication.run(MetricsApp.class, "--debug");
        }
    }

    @Configuration
    static class DataSourceConfig {
        public DataSourceConfig(DataSource dataSource,
                                Collection<DataSourcePoolMetadataProvider> metadataProviders,
                                MeterRegistry registry) {
            SpringMeters.monitor(
                    registry,
                    dataSource,
                    metadataProviders,
                "data_source");
        }
    }
}
