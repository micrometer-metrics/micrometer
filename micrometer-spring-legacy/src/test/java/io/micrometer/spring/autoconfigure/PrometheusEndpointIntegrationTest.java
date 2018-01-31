package io.micrometer.spring.autoconfigure;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = MetricsAutoConfigurationTest.MetricsApp.class,
    properties = {"management.security.enabled=false"})
public class PrometheusEndpointIntegrationTest {

    @Autowired
    TestRestTemplate testRestTemplate;

    @Test
    public void producesTextPlain() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> result = testRestTemplate.exchange("/prometheus", HttpMethod.GET, request, String.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

}
