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
package io.micrometer.spring.autoconfigure.export.signalfx;

import io.micrometer.spring.autoconfigure.export.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring metrics export to SignalFX.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.signalfx")
public class SignalFxProperties extends StepRegistryProperties {
    /**
     * Your access token, found in your account settings at SignalFX. This property is required.
     */
    private String accessToken;

    /**
     * Uniquely identifies the app instance that is publishing metrics to SignalFx. Defaults to the local host name.
     */
    private String source;

    /**
     * The URI to ship metrics to. If you need to publish metrics to an internal proxy en route to
     * SignalFx, you can define the location of the proxy with this.
     */
    private String uri;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

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
}