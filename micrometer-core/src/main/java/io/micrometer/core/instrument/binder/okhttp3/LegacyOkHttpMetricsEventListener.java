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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

class LegacyOkHttpMetricsEventListener {

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

    private final MeterRegistry registry;

    LegacyOkHttpMetricsEventListener(MeterRegistry registry, String requestsMetricName,
            Function<Request, String> urlMapper, Iterable<Tag> extraTags,
            Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags, Iterable<Tag> unknownRequestTags,
            boolean includeHostTag) {
        this.registry = registry;
        this.requestsMetricName = requestsMetricName;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.unknownRequestTags = unknownRequestTags;
        this.includeHostTag = includeHostTag;
    }

    @Nullable
    private static Method getMethod(Class<?>... parameterTypes) {
        try {
            return Request.class.getMethod("tag", parameterTypes);
        }
        catch (NoSuchMethodException e) {
            return null;
        }
    }

    private final String requestsMetricName;

    private final Function<Request, String> urlMapper;

    private final Iterable<Tag> extraTags;

    private final Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags;

    private final Iterable<Tag> unknownRequestTags;

    private final boolean includeHostTag;

    // VisibleForTesting
    void time(OkHttpMetricsEventListener.CallState state) {
        Request request = state.request;
        boolean requestAvailable = request != null;

        Iterable<Tag> tags = Tags
                .of("method", requestAvailable ? request.method() : TAG_VALUE_UNKNOWN, "uri", getUriTag(state, request),
                        "status", getStatusMessage(state.response, state.exception))
                .and(extraTags)
                .and(stream(contextSpecificTags.spliterator(), false)
                        .map(contextTag -> contextTag.apply(request, state.response)).collect(toList()))
                .and(getRequestTags(request)).and(generateTagsForRoute(request));

        if (includeHostTag) {
            tags = Tags.of(tags).and("host", requestAvailable ? request.url().host() : TAG_VALUE_UNKNOWN);
        }

        Timer.builder(this.requestsMetricName).tags(tags).description("Timer of OkHttp operation").register(registry)
                .record(registry.config().clock().monotonicTime() - state.startTime, TimeUnit.NANOSECONDS);
    }

    private Tags generateTagsForRoute(@Nullable Request request) {
        if (request == null) {
            return TAGS_TARGET_UNKNOWN;
        }
        return Tags.of(TAG_TARGET_SCHEME, request.url().scheme(), TAG_TARGET_HOST, request.url().host(),
                TAG_TARGET_PORT, Integer.toString(request.url().port()));
    }

    private String getUriTag(OkHttpMetricsEventListener.CallState state, @Nullable Request request) {
        if (request == null) {
            return TAG_VALUE_UNKNOWN;
        }
        return state.response != null && (state.response.code() == 404 || state.response.code() == 301) ? "NOT_FOUND"
                : urlMapper.apply(request);
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

    private String getStatusMessage(@Nullable Response response, @Nullable IOException exception) {
        if (exception != null) {
            return "IO_ERROR";
        }

        if (response == null) {
            return "CLIENT_ERROR";
        }

        return Integer.toString(response.code());
    }

}
