/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.web.client;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MetricsRestTemplateCustomizer}.
 *
 * @author Jon Schneider
 */
public class MetricsRestTemplateCustomizerTest {

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private RestTemplate restTemplate = new RestTemplate();
    private MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);

    @Before
    public void before() {
        MetricsRestTemplateCustomizer customizer = new MetricsRestTemplateCustomizer(
                registry, new DefaultRestTemplateExchangeTagsProvider(), "http.client.requests");
        customizer.customize(restTemplate);
    }

    @Test
    public void interceptRestTemplate() {
        mockServer.expect(MockRestRequestMatchers.requestTo("/test/123"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("OK",
                        MediaType.APPLICATION_JSON));

        String result = restTemplate.getForObject("/test/{id}", String.class, 123);

        assertThat(registry.get("http.client.requests").meters())
                .anySatisfy(m -> assertThat(m.getId().getTags().stream().map(Tag::getKey)).doesNotContain("bucket"));

        assertThat(registry.get("http.client.requests")
                .tags("method", "GET", "uri", "/test/{id}", "status", "200")
                .timer().count()).isEqualTo(1L);

        assertThat(result).isEqualTo("OK");

        mockServer.verify();
    }

    @Issue("#283")
    @Test
    public void normalizeUriToContainLeadingSlash() {
        mockServer.expect(MockRestRequestMatchers.requestTo("test/123"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("OK",
                        MediaType.APPLICATION_JSON));

        String result = restTemplate.getForObject("test/{id}", String.class, 123);

        registry.get("http.client.requests").tags("uri", "/test/{id}").timer();
        assertThat(result).isEqualTo("OK");

        mockServer.verify();
    }

    @Test
    public void interceptRestTemplateWithUri() throws URISyntaxException {
        mockServer.expect(MockRestRequestMatchers.requestTo("http://localhost/test/123"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
                .andRespond(MockRestResponseCreators.withSuccess("OK",
                        MediaType.APPLICATION_JSON));

        String result = restTemplate.getForObject(new URI("http://localhost/test/123"), String.class);

        assertThat(result).isEqualTo("OK");

        registry.get("http.client.requests").tags("uri", "/test/123").timer();

        mockServer.verify();
    }
}
