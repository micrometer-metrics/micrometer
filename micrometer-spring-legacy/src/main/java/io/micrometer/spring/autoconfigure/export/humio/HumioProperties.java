/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.export.humio;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * {@link ConfigurationProperties} for configuring Humio metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.humio")
public class HumioProperties extends StepRegistryProperties {
    /**
     * Humio API token.
     */
    private String apiToken;

    /**
     * The repository name to write metrics to.
     */
    private String repository;

    /**
     * URI to ship metrics to. If you need to publish metrics to an internal proxy
     * en-route to Humio, you can define the location of the proxy with this.
     */
    private String uri;

    /**
     * Humio uses a concept called "tags" to decide which datasource to store metrics in. This concept
     * is distinct from Micrometer's notion of tags, which divides a metric along dimensional boundaries.
     * All metrics from this registry will be stored under a datasource defined by these tags.
     */
    private Map<String, String> tags;

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
