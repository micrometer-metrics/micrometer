/*
 * Copyright 2020 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.micrometer.cloudwatchembeddedmetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.cloudwatchlogs.emf.Constants;
import software.amazon.cloudwatchlogs.emf.config.Configuration;
import software.amazon.cloudwatchlogs.emf.environment.Environments;
import software.amazon.cloudwatchlogs.emf.model.StorageResolution;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchEmbeddedMetricsConfigTest {

    private Map<String, String> props = new HashMap<>();

    private CloudWatchEmbeddedMetricsConfig config = props::get;

    private CloudWatchEmbeddedMetricsConfig configurationWithEmfConfig(Configuration emfConfig) {
        return new CloudWatchEmbeddedMetricsConfig() {
            @Override
            public String get(String key) {
                return props.get(key);
            }

            @Override
            public Configuration emfConfiguration() {
                return emfConfig;
            }
        };
    }

    @BeforeEach
    void setup() {
        props = new HashMap<>();
        config = props::get;
    }

    @Test
    void logGroupName() {
        // Without anything set, our config should return null
        assertThat(config.logGroupName()).isNull();

        // When the EMF config environment variable is set, but our config doesn't
        // override it, we should use the
        // value from the environment
        config = configurationWithEmfConfig(new Configuration(null, null, "my-log-group", null, null,
                Environments.Unknown, Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        assertThat(config.logGroupName()).isEqualTo("my-log-group");

        // Lastly, even if the EMF environment variable is set, we should use a value if
        // it's configured on our config
        // object
        props.put("cloudwatchemf.logGroupName", "my-other-log-group");
        assertThat(config.logGroupName()).isEqualTo("my-other-log-group");
    }

    @Test
    void logStreamName() {
        assertThat(config.logGroupName()).isNull();

        config = configurationWithEmfConfig(new Configuration(null, null, null, "my-log-stream", null,
                Environments.Unknown, Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        assertThat(config.logStreamName()).isEqualTo("my-log-stream");

        props.put("cloudwatchemf.logStreamName", "my-other-log-stream");
        assertThat(config.logStreamName()).isEqualTo("my-other-log-stream");
    }

    @Test
    void serviceType() {
        assertThat(config.serviceType()).isNull();

        config = configurationWithEmfConfig(new Configuration(null, "WebServer", null, null, null, Environments.Unknown,
                Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        assertThat(config.serviceType()).isEqualTo("WebServer");

        props.put("cloudwatchemf.serviceType", "MailServer");
        assertThat(config.serviceType()).isEqualTo("MailServer");
    }

    @Test
    void serviceName() {
        assertThat(config.serviceName()).isNull();

        config = configurationWithEmfConfig(new Configuration("my-service", null, null, null, null,
                Environments.Unknown, Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        assertThat(config.serviceName()).isEqualTo("my-service");

        props.put("cloudwatchemf.serviceName", "my-other-service");
        assertThat(config.serviceName()).isEqualTo("my-other-service");
    }

    @Test
    void namespace() {
        // Namespace is not settable via EMF environment variables
        assertThat(config.namespace()).isNull();

        props.put("cloudwatchemf.namespace", "my-namespace");
        assertThat(config.namespace()).isEqualTo("my-namespace");
    }

    @Test
    void agentEndpoint() {
        assertThat(config.agentEndpoint()).isNull();

        config = configurationWithEmfConfig(new Configuration(null, null, null, null, "https://127.0.0.1:443",
                Environments.Unknown, Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        assertThat(config.agentEndpoint()).isEqualTo("https://127.0.0.1:443");

        props.put("cloudwatchemf.agentEndpoint", "https://example.com:443");
        assertThat(config.agentEndpoint()).isEqualTo("https://example.com:443");
    }

    @Test
    void asyncBufferSize() {
        config = configurationWithEmfConfig(new Configuration(null, null, null, null, "https://127.0.0.1:443",
                Environments.Unknown, Constants.DEFAULT_ASYNC_BUFFER_SIZE));

        // EMF environment config fetcher will always respond with a default value
        assertThat(config.asyncBufferSize()).isEqualTo(Constants.DEFAULT_ASYNC_BUFFER_SIZE);

        props.put("cloudwatchemf.asyncBufferSize", "10");
        assertThat(config.asyncBufferSize()).isEqualTo(10);
    }

    @Test
    void useDefaultDimensions() {
        // not settable via EMF environment config
        assertThat(config.useDefaultDimensions()).isTrue();

        props.put("cloudwatchemf.useDefaultDimensions", "false");
        assertThat(config.useDefaultDimensions()).isFalse();
    }

    @Test
    void storageResolution() {
        props.put("cloudwatchemf.step", "1m");
        assertThat(config.storageResolution()).isEqualTo(StorageResolution.STANDARD);

        props.put("cloudwatchemf.step", "10s");
        assertThat(config.storageResolution()).isEqualTo(StorageResolution.HIGH);
    }

    @Test
    void valid() {
        assertThat(config.validate().isValid()).isTrue();
    }

}
