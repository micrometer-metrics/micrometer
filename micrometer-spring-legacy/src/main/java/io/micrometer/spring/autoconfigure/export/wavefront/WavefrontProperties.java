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
package io.micrometer.spring.autoconfigure.export.wavefront;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring Wavefront metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties("management.metrics.export.wavefront")
public class WavefrontProperties extends StepRegistryProperties {
    /**
     * The URI to publish metrics to. The URI could represent a Wavefront sidecar or the
     * Wavefront API host. This host could also represent an internal proxy set up in your environment
     * that forwards metrics data to the Wavefront API host.
     *
     * If publishing metrics to a Wavefront proxy (as described in https://docs.wavefront.com/proxies_installing.html),
     * the host must be in the proxy://HOST:PORT format.
     */
    private String uri;

    /**
     * Uniquely identifies the app instance that is publishing metrics to Wavefront. Defaults to the local host name.
     */
    private String source;

    /**
     * Required when publishing directly to the Wavefront API host, otherwise does nothing.
     */
    private String apiToken;

    /**
     * Wavefront metrics are grouped hierarchically by name in the UI. Setting a global prefix separates
     * metrics originating from this app's whitebox instrumentation from those originating from other Wavefront
     * integrations.
     */
    private String globalPrefix;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getGlobalPrefix() {
        return globalPrefix;
    }

    public void setGlobalPrefix(String globalPrefix) {
        this.globalPrefix = globalPrefix;
    }
}
