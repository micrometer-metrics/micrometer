/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.elastic;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for integration tests on {@link ElasticMeterRegistry}.
 *
 * @author Johnny Lim
 */
@Testcontainers
@Tag("docker")
abstract class AbstractElasticsearchMeterRegistryIntegrationTest {

    private static final String USER = "elastic";
    private static final String PASSWORD = "changeme";

    @Container
    private final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(getDockerImageName(getVersion()));

    private final HttpSender httpSender = new HttpUrlConnectionSender();

    private String host;
    private ElasticMeterRegistry registry;

    protected abstract String getVersion();

    @BeforeEach
    void setUp() {
        host = "http://" + elasticsearch.getHttpHostAddress();

        ElasticConfig config = new ElasticConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(10);
            }

            @Override
            public String host() {
                return host;
            }

            @Override
            public String userName() {
                return USER;
            }

            @Override
            public String password() {
                return PASSWORD;
            }
        };
        registry = ElasticMeterRegistry.builder(config).build();
    }

    @Test
    void indexTemplateShouldApply() throws Throwable {
        String response = sendHttpGet(host);
        String versionNumber = JsonPath.parse(response).read("$.version.number");
        assertThat(versionNumber).isEqualTo(getVersion());

        Counter counter = registry.counter("test.counter");
        counter.increment();

        registry.publish();

        String indexName = registry.indexName();
        String mapping = sendHttpGet(host + "/" + indexName + "/_mapping");
        String countType = JsonPath.parse(mapping).read(getCountTypePath(indexName));
        assertThat(countType).isEqualTo("double");
    }

    protected String getCountTypePath(String indexName) {
        return "$." + indexName + ".mappings.doc.properties.count.type";
    }

    private String sendHttpGet(String uri) throws Throwable {
        return httpSender.get(uri).withBasicAuthentication(USER, PASSWORD).send().body();
    }

    private static String getDockerImageName(String version) {
        return "docker.elastic.co/elasticsearch/elasticsearch-oss:" + version;
    }

}
