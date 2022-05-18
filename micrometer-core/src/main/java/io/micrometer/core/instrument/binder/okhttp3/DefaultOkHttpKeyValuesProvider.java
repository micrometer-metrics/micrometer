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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

@NonNullApi
@NonNullFields
public class DefaultOkHttpKeyValuesProvider implements OkHttpKeyValuesProvider {

    static final boolean REQUEST_TAG_CLASS_EXISTS;

    static {
        REQUEST_TAG_CLASS_EXISTS = getMethod(Class.class) != null;
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

    private static final String TAG_TARGET_SCHEME = "target.scheme";

    private static final String TAG_TARGET_HOST = "target.host";

    private static final String TAG_TARGET_PORT = "target.port";

    private static final String TAG_VALUE_UNKNOWN = "UNKNOWN";

    private static final KeyValues TAGS_TARGET_UNKNOWN = KeyValues.of(TAG_TARGET_SCHEME, TAG_VALUE_UNKNOWN, TAG_TARGET_HOST,
            TAG_VALUE_UNKNOWN, TAG_TARGET_PORT, TAG_VALUE_UNKNOWN);


    @Override
    public KeyValues getLowCardinalityKeyValues(OkHttpContext context) {
        OkHttpMetricsEventListener.CallState state = context.getState();
        Request request = state.request;
        boolean requestAvailable = request != null;
        Function<Request, String> urlMapper = context.getUrlMapper();
        Iterable<Tag> extraTags = context.getExtraTags();
        Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags = context.getContextSpecificTags();
        Iterable<Tag> unknownRequestTags = context.getUnknownRequestTags();
        boolean includeHostTag = context.isIncludeHostTag();
        KeyValues keyValues = KeyValues.of("method", requestAvailable ? request.method() : TAG_VALUE_UNKNOWN, "uri", getUriTag(urlMapper, state, request),
                        "status", getStatusMessage(state.response, state.exception))
                .and(tagsToKeyValues(stream(extraTags.spliterator(), false)))
                .and(stream(contextSpecificTags.spliterator(), false)
                        .map(contextTag -> contextTag.apply(request, state.response))
                        .map(tag -> KeyValue.of(tag.getKey(), tag.getValue()))
                        .collect(toList()))
                .and(getRequestTags(request, tagsToKeyValues(stream(unknownRequestTags.spliterator(), false))))
                .and(generateTagsForRoute(request));
        if (includeHostTag) {
            keyValues = KeyValues.of(keyValues).and("host", requestAvailable ? request.url().host() : TAG_VALUE_UNKNOWN);
        }
        return keyValues;
    }

    private String getUriTag(Function<Request, String> urlMapper, OkHttpMetricsEventListener.CallState state, @Nullable Request request) {
        if (request == null) {
            return TAG_VALUE_UNKNOWN;
        }
        return state.response != null && (state.response.code() == 404 || state.response.code() == 301) ? "NOT_FOUND"
                : urlMapper.apply(request);
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

    private Iterable<KeyValue> getRequestTags(@Nullable Request request, Iterable<KeyValue> unknownRequestTags) {
        if (request == null) {
            return unknownRequestTags;
        }
        if (REQUEST_TAG_CLASS_EXISTS) {
            Tags requestTag = request.tag(Tags.class);
            if (requestTag != null) {
                return tagsToKeyValues(requestTag.stream());
            }
        }
        Object requestTag = request.tag();
        if (requestTag instanceof Tags) {
            return tagsToKeyValues(((Tags) requestTag).stream());
        }
        return KeyValues.empty();
    }

    @NotNull
    private List<KeyValue> tagsToKeyValues(Stream<Tag> requestTag) {
        return requestTag.map(tag -> KeyValue.of(tag.getKey(), tag.getValue())).collect(Collectors.toList());
    }

    private KeyValues generateTagsForRoute(@Nullable Request request) {
        if (request == null) {
            return TAGS_TARGET_UNKNOWN;
        }
        return KeyValues.of(TAG_TARGET_SCHEME, request.url().scheme(), TAG_TARGET_HOST, request.url().host(),
                TAG_TARGET_PORT, Integer.toString(request.url().port()));
    }
}
