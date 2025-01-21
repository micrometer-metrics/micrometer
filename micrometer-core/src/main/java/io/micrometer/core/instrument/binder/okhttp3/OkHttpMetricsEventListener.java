/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.http.Outcome;
import okhttp3.EventListener;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link EventListener} for collecting metrics from {@link OkHttpClient}.
 * <p>
 * {@literal uri} tag is usually limited to URI patterns to mitigate tag cardinality
 * explosion but {@link OkHttpClient} doesn't provide URI patterns. We provide
 * {@value OkHttpMetricsEventListener#URI_PATTERN} header to support {@literal uri} tag or
 * you can configure a {@link Builder#uriMapper(Function) URI mapper} to provide your own
 * tag values for {@literal uri} tag.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Nurettin Yilmaz
 * @author Johnny Lim
 */
@NonNullApi
@NonNullFields
public class OkHttpMetricsEventListener extends EventListener {

    /**
     * Header name for URI patterns which will be used for tag values.
     */
    public static final String URI_PATTERN = "URI_PATTERN";

    private static final boolean REQUEST_TAG_CLASS_EXISTS;

    static {
        REQUEST_TAG_CLASS_EXISTS = getMethod(Class.class) != null;
    }

    private static final String TAG_TARGET_SCHEME = "target.scheme";

    private static final String TAG_TARGET_HOST = "target.host";

    private static final String TAG_TARGET_PORT = "target.port";

    private static final String TAG_VALUE_UNKNOWN = "UNKNOWN";

    private static final Tags TAGS_TARGET_UNKNOWN = Tags.of(TAG_TARGET_SCHEME, TAG_VALUE_UNKNOWN, TAG_TARGET_HOST,
            TAG_VALUE_UNKNOWN, TAG_TARGET_PORT, TAG_VALUE_UNKNOWN);

    @Nullable
    private static Method getMethod(Class<?>... parameterTypes) {
        try {
            return Request.class.getMethod("tag", parameterTypes);
        }
        catch (NoSuchMethodException e) {
            return null;
        }
    }

    private final MeterRegistry registry;

    private final String requestsMetricName;

    private final Function<Request, String> urlMapper;

    private final Iterable<Tag> extraTags;

    private final Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags;

    private final Iterable<Tag> unknownRequestTags;

    private final boolean includeHostTag;

    // VisibleForTesting
    final ConcurrentMap<Call, CallState> callState = new ConcurrentHashMap<>();

    protected OkHttpMetricsEventListener(MeterRegistry registry, String requestsMetricName,
            Function<Request, String> urlMapper, Iterable<Tag> extraTags,
            Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags) {
        this(registry, requestsMetricName, urlMapper, extraTags, contextSpecificTags, emptyList(), true);
    }

    OkHttpMetricsEventListener(MeterRegistry registry, String requestsMetricName, Function<Request, String> urlMapper,
            Iterable<Tag> extraTags, Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags,
            Iterable<String> requestTagKeys, boolean includeHostTag) {
        this.registry = registry;
        this.requestsMetricName = requestsMetricName;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.includeHostTag = includeHostTag;

        List<Tag> unknownRequestTags = new ArrayList<>();
        for (String requestTagKey : requestTagKeys) {
            unknownRequestTags.add(Tag.of(requestTagKey, "UNKNOWN"));
        }
        this.unknownRequestTags = unknownRequestTags;
    }

    public static Builder builder(MeterRegistry registry, String name) {
        return new Builder(registry, name);
    }

    @Override
    public void callStart(Call call) {
        callState.put(call, new CallState(registry.config().clock().monotonicTime(), call.request()));
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
    public void callEnd(Call call) {
        callState.remove(call);
    }

    @Override
    public void responseHeadersEnd(Call call, Response response) {
        CallState state = callState.remove(call);
        if (state != null) {
            state.response = response;
            time(state);
        }
    }

    // VisibleForTesting
    void time(CallState state) {
        Request request = state.request;
        boolean requestAvailable = request != null;

        Iterable<Tag> tags = Tags
            .of("method", requestAvailable ? request.method() : TAG_VALUE_UNKNOWN, "uri", getUriTag(request), "status",
                    getStatusMessage(state.response, state.exception))
            .and(getStatusOutcome(state.response).asTag())
            .and(extraTags)
            .and(stream(contextSpecificTags.spliterator(), false)
                .map(contextTag -> contextTag.apply(request, state.response))
                .collect(toList()))
            .and(getRequestTags(request))
            .and(generateTagsForRoute(request));

        if (includeHostTag) {
            tags = Tags.of(tags).and("host", requestAvailable ? request.url().host() : TAG_VALUE_UNKNOWN);
        }

        Timer.builder(this.requestsMetricName)
            .tags(tags)
            .description("Timer of OkHttp operation")
            .register(registry)
            .record(registry.config().clock().monotonicTime() - state.startTime, TimeUnit.NANOSECONDS);
    }

    private Tags generateTagsForRoute(@Nullable Request request) {
        if (request == null) {
            return TAGS_TARGET_UNKNOWN;
        }
        return Tags.of(TAG_TARGET_SCHEME, request.url().scheme(), TAG_TARGET_HOST, request.url().host(),
                TAG_TARGET_PORT, Integer.toString(request.url().port()));
    }

    private String getUriTag(@Nullable Request request) {
        if (request == null) {
            return TAG_VALUE_UNKNOWN;
        }
        return urlMapper.apply(request);
    }

    private Iterable<Tag> getRequestTags(@Nullable Request request) {
        if (request == null) {
            return unknownRequestTags;
        }
        if (REQUEST_TAG_CLASS_EXISTS) {
            Tags requestTag = request.tag(Tags.class);
            if (requestTag != null) {
                return requestTag;
            }
        }
        Object requestTag = request.tag();
        if (requestTag instanceof Tags) {
            return (Tags) requestTag;
        }
        return Tags.empty();
    }

    private Outcome getStatusOutcome(@Nullable Response response) {
        if (response == null) {
            return Outcome.UNKNOWN;
        }

        return Outcome.forStatus(response.code());
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

    // VisibleForTesting
    static class CallState {

        final long startTime;

        @Nullable
        final Request request;

        @Nullable
        Response response;

        @Nullable
        IOException exception;

        CallState(long startTime, @Nullable Request request) {
            this.startTime = startTime;
            this.request = request;
        }

    }

    public static class Builder {

        private final MeterRegistry registry;

        private final String name;

        private Function<Request, String> uriMapper = (request) -> Optional.ofNullable(request.header(URI_PATTERN))
            .orElse("none");

        private Tags tags = Tags.empty();

        private Collection<BiFunction<Request, Response, Tag>> contextSpecificTags = new ArrayList<>();

        private boolean includeHostTag = true;

        private Iterable<String> requestTagKeys = Collections.emptyList();

        Builder(MeterRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        public Builder tags(Iterable<Tag> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        /**
         * Add a {@link Tag} to any already configured tags on this Builder.
         * @param tag tag to add
         * @return this builder
         * @since 1.5.0
         */
        public Builder tag(Tag tag) {
            this.tags = this.tags.and(tag);
            return this;
        }

        /**
         * Add a context-specific tag.
         * @param contextSpecificTag function to create a context-specific tag
         * @return this builder
         * @since 1.5.0
         */
        public Builder tag(BiFunction<Request, Response, Tag> contextSpecificTag) {
            this.contextSpecificTags.add(contextSpecificTag);
            return this;
        }

        public Builder uriMapper(Function<Request, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Historically, OkHttp Metrics provided by {@link OkHttpMetricsEventListener}
         * included a {@code host} tag for the target host being called. To align with
         * other HTTP client metrics, this was changed to {@code target.host}, but to
         * maintain backwards compatibility the {@code host} tag can also be included. By
         * default, {@code includeHostTag} is {@literal true} so both tags are included.
         * @param includeHostTag whether to include the {@code host} tag
         * @return this builder
         * @since 1.5.0
         */
        public Builder includeHostTag(boolean includeHostTag) {
            this.includeHostTag = includeHostTag;
            return this;
        }

        /**
         * Tag keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         *
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @param requestTagKeys request tag keys
         * @return this builder
         * @since 1.3.9
         */
        public Builder requestTagKeys(String... requestTagKeys) {
            return requestTagKeys(Arrays.asList(requestTagKeys));
        }

        /**
         * Tag keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         *
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @param requestTagKeys request tag keys
         * @return this builder
         * @since 1.3.9
         */
        public Builder requestTagKeys(Iterable<String> requestTagKeys) {
            this.requestTagKeys = requestTagKeys;
            return this;
        }

        public OkHttpMetricsEventListener build() {
            return new OkHttpMetricsEventListener(registry, name, uriMapper, tags, contextSpecificTags, requestTagKeys,
                    includeHostTag);
        }

    }

}
