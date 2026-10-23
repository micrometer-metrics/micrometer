/*
 * Copyright 2019 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    // Testing against a pre-7.8 version to verify behavior without
    // composable index templates support.
    // See
    // https://www.elastic.co/guide/en/elasticsearch/reference/7.8/index-templates.html
    protected static final String IMAGE_NAME_7 = getImageName("elasticsearch7-image.name");

    protected static final String IMAGE_NAME_8 = getImageName("elasticsearch8-image.name");

    protected static final String IMAGE_NAME_9 = getImageName("elasticsearch9-image.name");

    protected static final String USER = "elastic";

    protected static final String PASSWORD = "changeme";

    @Container
    private final ElasticsearchContainer elasticsearch = getContainer();

    protected final HttpSender httpSender = new HttpUrlConnectionSender();

    protected String host;

    private ElasticMeterRegistry registry;

    protected abstract String getDockerImageName();

    @BeforeEach
    void setUp() {
        host = "http://" + elasticsearch.getHttpHostAddress();
        registry = ElasticMeterRegistry.builder(getConfig()).build();
    }

    @Test
    void indexTemplateShouldApply() throws Throwable {
        String response = sendHttpGet(host);
        String versionNumber = JsonPath.parse(response).read("$.version.number");
        assertThat(versionNumber).isEqualTo(DockerImageName.parse(getDockerImageName()).getVersionPart());

        Counter counter = registry.counter("test.counter");
        counter.increment();

        registry.publish();

        String indexName = registry.indexName();
        String mapping = sendHttpGet(host + "/" + indexName + "/_mapping");
        String countType = JsonPath.parse(mapping).read(getCountTypePath(indexName));
        assertThat(countType).isEqualTo("double");
    }

    protected String getCountTypePath(String indexName) {
        return "$." + indexName + ".mappings.properties.count.type";
    }

    protected ElasticsearchContainer getContainer() {
        return new ElasticsearchContainer(DockerImageName.parse(getDockerImageName())).withPassword(PASSWORD);
    }

    protected ElasticConfig getConfig() {
        return new ElasticConfig() {
            @Override
            public @Nullable String get(String key) {
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
    }

    private String sendHttpGet(String uri) throws Throwable {
        return httpSender.get(uri).withBasicAuthentication(USER, PASSWORD).send().body();
    }

    private static String getImageName(String systemProperty) {
        String imageName = System.getProperty(systemProperty);
        if (imageName == null) {
            throw new IllegalStateException("System property '" + systemProperty
                    + "' is not set. This should be set in the build configuration for running from the command line. If you are running Elasticsearch integration tests from an IDE, set the system property to the desired image name.");
        }
        return imageName;
    }

}
