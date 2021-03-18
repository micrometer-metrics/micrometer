/**
 * Copyright 2021 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.influx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.BasicCredentials;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.config.validate.ValidationException;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests to check compatibility of {@link InfluxMeterRegistry} with InfluxDB v1 and InfluxDB v2.
 *
 * @author Jakub Bednar
 */
@ExtendWith(WiremockResolver.class)
class InfluxMeterRegistryVersionsTest {

    @Test
    void writeToV1Token(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(any(urlPathEqualTo("/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(InfluxApiVersion.V1, server);

        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=my-db"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withHeader("Authorization", equalTo("Bearer my-token")));
    }

    @Test
    void writeToV1BasicAuth(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/write"))
                .withBasicAuth("user", "pass")
                .willReturn(aResponse().withStatus(204)));

        Map<String, String> props = new HashMap<>();
        InfluxConfig config = props::get;
        props.put("influx.uri", server.baseUrl());
        props.put("influx.userName", "user");
        props.put("influx.password", "pass");

        publishSimpleStat(config);

        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withBasicAuth(new BasicCredentials("user", "pass")));
    }

    @Test
    void writeToV1TokenPreferredOverBasicAuth(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/write"))
                .willReturn(aResponse().withStatus(204)));

        Map<String, String> props = new HashMap<>();
        InfluxConfig config = props::get;
        props.put("influx.uri", server.baseUrl());
        props.put("influx.userName", "user");
        props.put("influx.password", "pass");
        props.put("influx.token", "some-token");

        publishSimpleStat(config);

        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=mydb"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withHeader("Authorization", equalTo("Bearer some-token")));
    }

    @Test
    void writeToV2Token(@WiremockResolver.Wiremock WireMockServer server) {
        server.stubFor(any(urlPathEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        publishSimpleStat(InfluxApiVersion.V2, server);

        server.verify(postRequestedFor(urlEqualTo("/api/v2/write?&precision=ms&bucket=my-bucket&org=my-org"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withHeader("Authorization", equalTo("Token my-token")));
    }

    @Test
    void writeToV1WithRP(@WiremockResolver.Wiremock WireMockServer server) {
        Map<String, String> props = new HashMap<>();
        InfluxConfig config = props::get;
        props.put("influx.uri", server.baseUrl());
        props.put("influx.db", "my-db");
        props.put("influx.token", "my-token");
        props.put("influx.retentionPolicy", "one_day_only");

        publishSimpleStat(config);

        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=my-db&rp=one_day_only"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withHeader("Authorization", equalTo("Bearer my-token")));
    }

    @Test
    void writeToV2TokenRequired(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlPathEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        InfluxConfig config = new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public String org() {
                return "my-org";
            }

            @Override
            public InfluxApiVersion apiVersion() {
                return InfluxApiVersion.V2;
            }
        };

        Assertions.assertThatThrownBy(() -> publishSimpleStat(config))
                .hasMessage("influx.apiVersion was 'V2' but it requires 'token' is also configured")
                .isInstanceOf(ValidationException.class);

        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void writeToV2TokenNotBlank(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlPathEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        InfluxConfig config = new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public String token() {
                return "";
            }

            @Override
            public String org() {
                return "my-org";
            }

            @Override
            public InfluxApiVersion apiVersion() {
                return InfluxApiVersion.V2;
            }
        };

        Assertions.assertThatThrownBy(() -> publishSimpleStat(config))
                .hasMessage("influx.apiVersion was 'V2' but it requires 'token' is also configured")
                .isInstanceOf(ValidationException.class);

        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void writeToV2OrgRequired(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlPathEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        InfluxConfig config = new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public String token() {
                return "my-token";
            }

            @Override
            public InfluxApiVersion apiVersion() {
                return InfluxApiVersion.V2;
            }
        };

        Assertions.assertThatThrownBy(() -> publishSimpleStat(config))
                .hasMessage("influx.apiVersion was 'V2' but it requires 'org' is also configured")
                .isInstanceOf(ValidationException.class);

        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void writeToV2OrgNotBlank(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlPathEqualTo("/api/v2/write"))
                .willReturn(aResponse().withStatus(204)));

        InfluxConfig config = new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public String token() {
                return "my-token";
            }

            @Override
            public String org() {
                return "";
            }

            @Override
            public InfluxApiVersion apiVersion() {
                return InfluxApiVersion.V2;
            }
        };

        Assertions.assertThatThrownBy(() -> publishSimpleStat(config))
                .hasMessage("influx.apiVersion was 'V2' but it requires 'org' is also configured")
                .isInstanceOf(ValidationException.class);

        server.verify(0, postRequestedFor(anyUrl()));
    }

    @Test
    void bucketIsAliasForDb() {
        InfluxConfig config = key -> null;

        assertThat(config.bucket()).isEqualTo("mydb");
    }

    @Test
    void bucketOrDbShouldBeSpecified() {
        Map<String, String> props = new HashMap<>();
        InfluxConfig config = props::get;
        props.put("influx.db", "");

        assertThat(config.validate().failures().stream()
                .map(Validated.Invalid::getMessage))
                .containsExactly("db or bucket should be specified");
    }

    @Test
    void createStorageV1(@WiremockResolver.Wiremock WireMockServer server) {

        server.stubFor(any(urlPathEqualTo("/query"))
                .willReturn(aResponse().withStatus(200).withBody("{\"results\":[{\"statement_id\":0}]}")));
        server.stubFor(any(urlPathEqualTo("/write"))
                .willReturn(aResponse().withStatus(204)));

        Map<String, String> props = new HashMap<>();
        InfluxConfig config = props::get;
        props.put("influx.uri", server.baseUrl());
        props.put("influx.db", "my-db");
        props.put("influx.token", "my-token");
        props.put("influx.autoCreateDb", "true");

        publishSimpleStat(config);

        server.verify(postRequestedFor(urlEqualTo("/query?q=CREATE+DATABASE+%22my-db%22"))
                .withHeader("Authorization", equalTo("Bearer my-token")));
        server.verify(postRequestedFor(urlEqualTo("/write?consistency=one&precision=ms&db=my-db"))
                .withRequestBody(equalTo("my_counter,metric_type=counter value=0 1"))
                .withHeader("Authorization", equalTo("Bearer my-token")));
    }

    @Test
    void apiVersionDefault() {
        InfluxConfig config = key -> null;

        assertThat(config.apiVersion()).isEqualTo(InfluxApiVersion.V1);
    }

    @Test
    void apiVersionConfiguredToV1() {
        Map<String, String> props = Collections.singletonMap("influx.apiVersion", "v1");
        InfluxConfig config = props::get;

        assertThat(config.apiVersion()).isEqualTo(InfluxApiVersion.V1);
    }

    @Test
    void apiVersionConfiguredToV2() {
        Map<String, String> props = Collections.singletonMap("influx.apiVersion", "v2");
        InfluxConfig config = props::get;

        assertThat(config.apiVersion()).isEqualTo(InfluxApiVersion.V2);
    }

    @Test
    void apiVersionDefaultV2WhenOrgIsDefined() {
        Map<String, String> props = Collections.singletonMap("influx.org", "my-org");
        InfluxConfig config = props::get;

        assertThat(config.apiVersion()).isEqualTo(InfluxApiVersion.V2);
    }

    private void publishSimpleStat(@NonNull InfluxApiVersion apiVersion, WireMockServer server) {
        InfluxConfig config = new InfluxConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }

            @Override
            public String token() {
                return "my-token";
            }

            @Override
            public String org() {
                return "my-org";
            }

            @Override
            public String db() {
                return "my-db";
            }

            @Override
            public String bucket() {
                return "my-bucket";
            }

            @Override
            public InfluxApiVersion apiVersion() {
                return apiVersion;
            }
        };

        publishSimpleStat(config);
    }

    private void publishSimpleStat(InfluxConfig config) {
        InfluxMeterRegistry registry = new InfluxMeterRegistry(config, new MockClock());

        Counter.builder("my.counter")
                .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
                .description("metric description")
                .register(registry)
                .increment(Math.PI);
        registry.publish();
    }
}
