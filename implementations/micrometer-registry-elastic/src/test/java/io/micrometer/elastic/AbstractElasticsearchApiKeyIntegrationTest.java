/*
 * Copyright 2021 VMware, Inc.
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
import io.micrometer.core.ipc.http.HttpSender;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Base class for testing Elasticsearch backend with API key authentication.
 *
 * @author Tommy Ludwig
 * @author Johnny Lim
 */
abstract class AbstractElasticsearchApiKeyIntegrationTest extends AbstractElasticsearchMeterRegistryIntegrationTest {

    @Override
    protected ElasticConfig getConfig() {
        String apiKeyCredentials;
        // Create API key
        try {
            HttpSender.Response response = httpSender.post(host + "/_security/api_key")
                .withBasicAuthentication(USER, PASSWORD)
                .withJsonContent("{\"name\": \"my-api-key\",\"expiration\": \"1h\"}")
                .send();
            String body = response.body();
            String id = JsonPath.parse(body).read("$.id");
            String apiKey = JsonPath.parse(body).read("$.api_key");
            apiKeyCredentials = Base64.getEncoder()
                .encodeToString((id + ":" + apiKey).getBytes(StandardCharsets.UTF_8));
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return new ElasticConfig() {
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
            public String apiKeyCredentials() {
                return apiKeyCredentials;
            }
        };
    }

    @Override
    protected ElasticsearchContainer getContainer() {
        return super.getContainer().withEnv("xpack.security.authc.api_key.enabled", "true");
    }

}
