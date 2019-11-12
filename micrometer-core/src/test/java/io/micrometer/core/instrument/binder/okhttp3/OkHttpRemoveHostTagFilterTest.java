/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.okhttp3;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.util.function.Function;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(WiremockResolver.class)
class OkHttpRemoveHostTagFilterTest {

    private static final String URI_EXAMPLE_VALUE = "uriExample";
    private static final Function<Request, String> URI_MAPPER = req -> URI_EXAMPLE_VALUE;

    private OkHttpRemoveHostTagFilter filter = new OkHttpRemoveHostTagFilter();
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private OkHttpClient client = new OkHttpClient.Builder()
            .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                    .tags(Tags.of("foo", "bar"))
                    .uriMapper(URI_MAPPER)
                    .build())
            .build();

    @Test
    void removeTag(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder()
                .url(server.baseUrl())
                .build();

        client.newCall(request).execute().close();

        Meter.Id original = registry.get("okhttp.requests")
                .tags("foo", "bar", "status", "200", "uri", URI_EXAMPLE_VALUE)
                .timer().getId();
        Meter.Id actual = filter.map(original);

        assertThat(actual).isNotEqualTo(original);
        assertThat(actual.getTags()).allMatch(tag -> !tag.getKey().equals("host"));
        assertThat(original.getTags()).anyMatch(tag -> tag.getKey().equals("host"));
    }

}
