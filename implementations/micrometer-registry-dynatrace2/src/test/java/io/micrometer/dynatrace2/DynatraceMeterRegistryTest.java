/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.dynatrace2;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.validate.ValidationException;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_KEY_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_VALUE_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.METRIC_KEY_MAX_LENGTH;
import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.METRIC_LINE_MAX_LENGTH;

/**
 * Tests for {@link DynatraceMeterRegistry}.
 *
 * @author Oriol Barcelona
 * @author David Mass
 */
@ExtendWith(WiremockResolver.class)
class DynatraceMeterRegistryTest implements WithAssertions {

    private static final String API_TOKEN = "DT-API-TOKEN";
    private static final UrlPattern METRICS_INGESTION_URL = urlEqualTo(MetricsApiIngestion.METRICS_INGESTION_URL);
    private static final StringValuePattern TEXT_PLAIN_CONTENT_TYPE = equalTo("text/plain");

    WireMockServer dtApiServer;
    Clock clock;
    DynatraceMeterRegistry meterRegistry;

    @BeforeEach
    void setupServerAndConfig(@Wiremock WireMockServer server) {
        this.dtApiServer = server;
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return API_TOKEN;
            }

            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String entityId() { return "HOST-06F288EE2A930951"; }
        };
        clock = new MockClock();
        meterRegistry = DynatraceMeterRegistry.builder(config)
                .clock(clock)
                .build();
        dtApiServer.stubFor(post(METRICS_INGESTION_URL)
                .willReturn(aResponse().withStatus(202)));
    }

    @Test
    void shouldThrowValidationException_whenUriIsMissingInConfig() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiToken() {
                return "apiToken";
            }
        };

        assertThatThrownBy(() -> DynatraceMeterRegistry.builder(config).build())
                .isExactlyInstanceOf(ValidationException.class);
    }

    @Test
    void shouldThrowValidationException_whenApiTokenIsMissingInConfig() {
        DynatraceConfig config = new DynatraceConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String uri() {
                return "uri";
            }
        };

        assertThatThrownBy(() -> DynatraceMeterRegistry.builder(config).build())
                .isExactlyInstanceOf(ValidationException.class);
    }

    @Test
    void shouldIngestAMetricThroughTheApi() {
        meterRegistry.gauge("cpu.temperature", 55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu.temperature,dt.entity.host=\"HOST-06F288EE2A930951\" 55"))
        );
    }

    @Test
    void shouldIngestAMetricThroughTheApi_whenHasDimensions() {
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("hostname", "server01", "cpu", "1"),
                55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu.temperature,cpu=\"1\",dt.entity.host=\"HOST-06F288EE2A930951\",hostname=\"server01\" 55"))
        );
    }

    @Test
    void shouldIngestMultipleMetricsThroughTheApi_whenSameMetricButDifferentDimensions() {
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("hostname", "server01", "cpu", "1"),
                55);
        meterRegistry.gauge(
                "cpu.temperature",
                Tags.of("hostname", "server01", "cpu", "2"),
                50);


        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines(
                        "cpu.temperature,cpu=\"1\",dt.entity.host=\"HOST-06F288EE2A930951\",hostname=\"server01\" 55",
                        "cpu.temperature,cpu=\"2\",dt.entity.host=\"HOST-06F288EE2A930951\",hostname=\"server01\" 50"
                        ))
        );
    }


    @Test
    void shouldMetricNameBeSanitized_whenSpecialChars() {
        meterRegistry.gauge(
                "cpu#temperature",
                Tags.of("hostname", "server01", "cpu", "1"),
                55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu_temperature,cpu=\"1\",dt.entity.host=\"HOST-06F288EE2A930951\",hostname=\"server01\" 55"))
        );
    }


    @Test
    void shouldAddCommonTag_whenEntityIdPropIsAdded() {
        meterRegistry.gauge(
                "cpu_temperature", 55);

        meterRegistry.publish();

        dtApiServer.verify(postRequestedFor(METRICS_INGESTION_URL)
                .withHeader("Content-Type", TEXT_PLAIN_CONTENT_TYPE)
                .withRequestBody(equalToMetricLines("cpu_temperature,dt.entity.host=\"HOST-06F288EE2A930951\" 55"))
        );
    }

    @Test
    void shouldSkipMetricLines_whenAreBiggerThanMaxLengthLimit() {
        String metricName = newString(METRIC_KEY_MAX_LENGTH);
        long metricValue = 55;
        int lengthForTags = METRIC_LINE_MAX_LENGTH - metricName.length() - String.valueOf(metricValue).length();
        int maxSizeTagLength = DIMENSION_KEY_MAX_LENGTH + DIMENSION_VALUE_MAX_LENGTH;
        int numberOfTags = (lengthForTags / maxSizeTagLength) + 1;

        List<Tag> tags = IntStream.range(0, numberOfTags)
                .mapToObj(String::valueOf)
                .map(this::maxSizeTag)
                .collect(Collectors.toList());

        meterRegistry.gauge(metricName, tags, metricValue);

        meterRegistry.publish();

        dtApiServer.verify(0, anyRequestedFor(METRICS_INGESTION_URL));
    }

    private Tag maxSizeTag(String prefix) {
        return Tag.of(
                prefix + newString(DIMENSION_KEY_MAX_LENGTH - prefix.length()),
                newString(DIMENSION_VALUE_MAX_LENGTH));
    }

    private String newString(int length) {
        return new String(new char[length]);
    }

    private StringValuePattern equalToMetricLines(String... lines) {
        return equalToMetricLines(clock.wallTime(), lines);
    }

    private StringValuePattern equalToMetricLines(long time, String... lines) {
        return equalTo(
                Stream.of(lines)
                        .map(line -> line + " " + time)
                        .collect(Collectors.joining(System.lineSeparator())));
    }
}
