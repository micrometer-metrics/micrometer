/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace;

import com.dynatrace.file.util.DynatraceFileBasedConfigurationProvider;
import com.dynatrace.metric.util.DynatraceMetricApiConstants;
import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DynatraceConfigTest {

    private static final String nonExistentConfigFileName = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        // Make sure that all tests use the default configuration, even if there's an
        // `endpoint.properties` file in place
        DynatraceFileBasedConfigurationProvider.getInstance()
            .forceOverwriteConfig(nonExistentConfigFileName, Duration.ofMillis(50));
    }

    @Test
    void invalid() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V1;
            }
        };

        List<Validated.Invalid<?>> failures = config.validate().failures();
        assertThat(failures).hasSize(3);
        assertThat(failures.stream().map(Validated::toString)).containsExactlyInAnyOrder(
                "Invalid{property='dynatrace.apiToken', value='null', message='is required'}",
                "Invalid{property='dynatrace.uri', value='null', message='is required'}",
                "Invalid{property='dynatrace.deviceId', value='', message='cannot be blank'}");
    }

    @Test
    void invalidOverrideTechnologyType() {
        Validated<?> validate = new DynatraceConfig() {
            @Override
            public String technologyType() {
                return "";
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V1;
            }
        }.validate();

        assertThat(validate.failures()).hasSize(4);
        assertThat(validate.failures().stream().map(Validated::toString)).containsExactlyInAnyOrder(
                "Invalid{property='dynatrace.apiToken', value='null', message='is required'}",
                "Invalid{property='dynatrace.uri', value='null', message='is required'}",
                "Invalid{property='dynatrace.deviceId', value='', message='cannot be blank'}",
                "Invalid{property='dynatrace.technologyType', value='', message='cannot be blank'}");
    }

    @Test
    void invalidMissingUriInV2() {
        Validated<?> validate = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }

            @Override
            public String uri() {
                return null;
            }
        }.validate();

        assertThat(validate.failures()).hasSize(1);
        assertThat(validate.failures().stream().map(Validated::toString))
            .containsExactlyInAnyOrder("Invalid{property='dynatrace.uri', value='null', message='is required'}");
    }

    @Test
    void invalidVersion() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiToken", "secret");
        properties.put("dynatrace.uri", "https://uri.dynatrace.com");
        properties.put("dynatrace.deviceId", "device");
        properties.put("dynatrace.apiVersion", "v-INVALID");
        DynatraceConfig config = properties::get;

        List<Validated.Invalid<?>> failures = config.validate().failures();
        assertThat(failures).hasSize(1);
        Validated.Invalid<?> failure = failures.get(0);
        assertThat(failure.getProperty()).isEqualTo("dynatrace.apiVersion");
        assertThat(failure.getValue()).isEqualTo("v-INVALID");
        assertThat(failure.getMessage()).startsWith("should be one of ");
        assertThat(failure.getReason()).isSameAs(InvalidReason.MALFORMED);
        assertThat(failure.getException()).isNull();
    }

    @Test
    void valid() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiToken", "secret");
        properties.put("dynatrace.uri", "https://uri.dynatrace.com");
        properties.put("dynatrace.deviceId", "device");
        DynatraceConfig config = properties::get;
        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void testFallbackToV1() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiToken", "secret");
        properties.put("dynatrace.uri", "https://uri.dynatrace.com");
        properties.put("dynatrace.deviceId", "device");

        DynatraceConfig config = properties::get;

        assertThat(config.validate().isValid()).isTrue();
        assertThat(config.apiVersion()).isSameAs(DynatraceApiVersion.V1);
    }

    @Test
    void testV2Defaults() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiVersion", "v2");
        DynatraceConfig config = properties::get;

        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);
        assertThat(config.apiToken()).isEmpty();
        assertThat(config.uri()).isSameAs(DynatraceMetricApiConstants.getDefaultOneAgentEndpoint());
        assertThat(config.deviceId()).isEmpty();
        assertThat(config.metricKeyPrefix()).isEmpty();
        assertThat(config.defaultDimensions()).isEmpty();
        assertThat(config.enrichWithDynatraceMetadata()).isTrue();
        assertThat(config.exportMeterMetadata()).isTrue();

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testV1Defaults() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiVersion", "v1");
        properties.put("dynatrace.apiToken", "my.token");
        properties.put("dynatrace.uri", "https://my.uri.com");
        properties.put("dynatrace.deviceId", "my.device.id");
        DynatraceConfig config = properties::get;

        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V1);
        assertThat(config.apiToken()).isEqualTo("my.token");
        assertThat(config.uri()).isEqualTo("https://my.uri.com");
        assertThat(config.deviceId()).isEqualTo("my.device.id");
        assertThat(config.metricKeyPrefix()).isEmpty();
        assertThat(config.defaultDimensions()).isEmpty();
        assertThat(config.enrichWithDynatraceMetadata()).isFalse();
        assertThat(config.exportMeterMetadata()).isFalse();

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testOneAgentEndpointWithDifferentPort() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiVersion", "v2");
        properties.put("dynatrace.uri", "http://localhost:13333/metrics/ingest");
        DynatraceConfig config = properties::get;

        assertThat(config.apiToken()).isEmpty();
        assertThat(config.uri()).isEqualTo("http://localhost:13333/metrics/ingest");
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testV2WithEndpointAndToken() {
        Map<String, String> properties = new HashMap<>();
        properties.put("dynatrace.apiVersion", "v2");
        properties.put("dynatrace.uri", "https://uri.dynatrace.com");
        properties.put("dynatrace.apiToken", "secret");

        DynatraceConfig config = properties::get;
        assertThat(config.apiToken()).isEqualTo("secret");
        assertThat(config.uri()).isEqualTo("https://uri.dynatrace.com");
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);

        Validated<?> validated = config.validate();
        assertThat(validated.isValid()).isTrue();
    }

    @Test
    void testDeviceIdNotSetFallsBackToV2() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }
        };

        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);
    }

    @Test
    void testDeviceIdSetFallsBackToV1() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String deviceId() {
                return "test";
            }
        };
        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V1);
    }

    @Test
    void testDeviceIdSetAndVersionOverwritten() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String deviceId() {
                return "test";
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }
        };

        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V2);
    }

    @Test
    void testDeviceIdNotSetAndVersionOverwritten() {
        // This is a nonsense config, v1 always needs a deviceId, but it shows that it is
        // possible
        // to overwrite the version.
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V1;
            }
        };

        assertThat(config.apiVersion()).isEqualTo(DynatraceApiVersion.V1);
    }

    @Test
    void testFileBasedConfig() throws IOException {
        String uuid = UUID.randomUUID().toString();
        final Path tempFile = Files.createTempFile(uuid, ".properties");

        Files.write(tempFile, ("DT_METRICS_INGEST_URL = https://your-dynatrace-ingest-url/api/v2/metrics/ingest\n"
                + "DT_METRICS_INGEST_API_TOKEN = YOUR.DYNATRACE.TOKEN")
            .getBytes());

        DynatraceFileBasedConfigurationProvider.getInstance()
            .forceOverwriteConfig(tempFile.toString(), Duration.ofMillis(50));

        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public DynatraceApiVersion apiVersion() {
                return DynatraceApiVersion.V2;
            }
        };

        await().atMost(1_000, MILLISECONDS).until(() -> config.apiToken().equals("YOUR.DYNATRACE.TOKEN"));
        assertThat(config.uri()).isEqualTo("https://your-dynatrace-ingest-url/api/v2/metrics/ingest");

        Files.write(tempFile, ("DT_METRICS_INGEST_URL = https://a-different-url/api/v2/metrics/ingest\n"
                + "DT_METRICS_INGEST_API_TOKEN = A.DIFFERENT.TOKEN")
            .getBytes());

        await().atMost(1_000, MILLISECONDS).until(() -> config.apiToken().equals("A.DIFFERENT.TOKEN"));
        assertThat(config.uri()).isEqualTo("https://a-different-url/api/v2/metrics/ingest");

        Files.deleteIfExists(tempFile);
    }

}
