/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.metrics.instrument.IdentityTagFormatter;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * @author Jon Schneider
 */
class MetricsRestTemplateInterceptorTest {
    @Test
    void interceptRestTemplate() {
        MeterRegistry registry = new SimpleMeterRegistry();

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(singletonList(new MetricsRestTemplateInterceptor(
                registry, new RestTemplateTagConfigurer(new IdentityTagFormatter()),
                "http_client_requests"
        )));

        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("OK", MediaType.APPLICATION_JSON));

        String s = restTemplate.getForObject("/test/{id}", String.class, 123);

        // the uri requires AOP to determine
        assertThat(registry.findMeter(Timer.class, "http_client_requests",
                "method", "GET", "uri", "none", "status", "200"))
                .containsInstanceOf(Timer.class)
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        assertEquals("OK", s);

        mockServer.verify();
    }
}
