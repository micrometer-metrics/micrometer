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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import okhttp3.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * {@link EventListener} for collecting metrics from {@link OkHttpClient}.
 *
 * {@literal uri} tag is usually limited to URI patterns to mitigate tag cardinality explosion but {@link OkHttpClient}
 * doesn't provide URI patterns. We provide {@value URI_PATTERN} header to support {@literal uri} tag or you can
 * configure a {@link Builder#uriMapper(Function) URI mapper} to provide your own tag values for {@literal uri} tag.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Nurettin Yilmaz
 */
@NonNullApi
@NonNullFields
public class OkHttpMetricsEventListener extends EventListener {

    /**
     * Header name for URI patterns which will be used for tag values.
     */
    public static final String URI_PATTERN = "URI_PATTERN";

    private final MeterRegistry registry;
    private final String requestsMetricName;
    private final Function<Request, String> urlMapper;
    private final Iterable<Tag> extraTags;
    private final ConcurrentMap<Call, CallState> callState = new ConcurrentHashMap<>();

    OkHttpMetricsEventListener(MeterRegistry registry, String requestsMetricName, Function<Request, String> urlMapper, Iterable<Tag> extraTags) {
        this.registry = registry;
        this.requestsMetricName = requestsMetricName;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
    }

    public static Builder builder(MeterRegistry registry, String name) {
        return new Builder(registry, name);
    }

    @Override
    public void callStart(Call call) {
        callState.put(call, new CallState(registry.config().clock().monotonicTime()));
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
        callState.computeIfPresent(call, (c, state) -> {
            state.request = request;
            return state;
        });
    }

    @Override
    public void callFailed(Call call, IOException e) {
        CallState state = callState.remove(call);
        if (state != null) {
            state.exception = e;
            time(state);
        }
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        CallState state = callState.remove(call);
        if (state != null) {
            state.response = response;
            time(state);
        }
    }

    private void time(CallState state) {
        Request request = state.request;
        boolean requestAvailable = request != null;

        String uri = state.response == null ? "UNKNOWN" :
            (state.response.code() == 404 || state.response.code() == 301 ? "NOT_FOUND" : urlMapper.apply(request));

        Tags dynamicTags = requestAvailable ? request.tag(Tags.class) : Tags.empty();

        Iterable<Tag> tags = Tags.concat(extraTags, Tags.of(
            "method", requestAvailable ? request.method() : "UNKNOWN",
            "uri", uri,
            "status", getStatusMessage(state.response, state.exception),
            "host", requestAvailable ? request.url().host() : "UNKNOWN"
        )).and(dynamicTags);

        Timer.builder(this.requestsMetricName)
            .tags(tags)
            .description("Timer of OkHttp operation")
            .register(registry)
            .record(registry.config().clock().monotonicTime() - state.startTime, TimeUnit.NANOSECONDS);
    }

    private String getStatusMessage(@Nullable Response response, @Nullable IOException exception) {
        if (exception != null) {
            return "IO_ERROR";
        }

        if (response == null) {
            return "CLIENT_ERROR";
        }

        return Integer.toString(response.code());
    }

    private static class CallState {
        final long startTime;
        @Nullable
        Request request;
        @Nullable
        Response response;
        @Nullable
        IOException exception;

        CallState(long startTime) {
            this.startTime = startTime;
        }
    }

    public static class Builder {
        private final MeterRegistry registry;
        private final String name;
        private Function<Request, String> uriMapper = (request) -> Optional.ofNullable(request.header(URI_PATTERN)).orElse("none");
        private Iterable<Tag> tags = Collections.emptyList();

        Builder(MeterRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        public Builder tags(Iterable<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public Builder uriMapper(Function<Request, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        public OkHttpMetricsEventListener build() {
            return new OkHttpMetricsEventListener(registry, name, uriMapper, tags);
        }
    }
}
