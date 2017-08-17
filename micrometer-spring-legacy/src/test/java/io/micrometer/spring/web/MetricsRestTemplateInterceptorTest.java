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
package io.micrometer.spring.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
public class MetricsRestTemplateInterceptorTest {
    @Test
    public void interceptRestTemplate() {
        MeterRegistry registry = new SimpleMeterRegistry();

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(singletonList(new MetricsRestTemplateInterceptor(
                registry, new RestTemplateTagConfigurer(),
                "http.client.requests"
        )));

        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("OK", MediaType.APPLICATION_JSON));

        String s = restTemplate.getForObject("/test/{id}", String.class, 123);

        // the uri requires AOP to determine
        assertThat(registry.find("http.client.requests").tags("method", "GET", "uri", "none", "status", "200").timer())
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        assertThat(s).isEqualTo("OK");

        mockServer.verify();
    }
}
