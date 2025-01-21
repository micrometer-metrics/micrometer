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
import io.micrometer.core.instrument.binder.http.Outcome;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationDocumentation.OkHttpLegacyLowCardinalityTags.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

@NonNullApi
@NonNullFields
public class DefaultOkHttpObservationConvention implements OkHttpObservationConvention {

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

    private static final KeyValues TAGS_TARGET_UNKNOWN = KeyValues.of(TAG_TARGET_SCHEME, TAG_VALUE_UNKNOWN,
            TAG_TARGET_HOST, TAG_VALUE_UNKNOWN, TAG_TARGET_PORT, TAG_VALUE_UNKNOWN);

    private final String metricName;

    public DefaultOkHttpObservationConvention(String metricName) {
        this.metricName = metricName;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(OkHttpContext context) {
        OkHttpObservationInterceptor.CallState state = context.getState();
        Request request = state.request;
        boolean requestAvailable = request != null;
        Function<Request, String> urlMapper = context.getUrlMapper();
        Iterable<KeyValue> extraTags = context.getExtraTags();
        Iterable<BiFunction<Request, Response, KeyValue>> contextSpecificTags = context.getContextSpecificTags();
        Iterable<KeyValue> unknownRequestTags = context.getUnknownRequestTags();
        boolean includeHostTag = context.isIncludeHostTag();
        // TODO: Tags to key values and back - maybe we can improve this?
        KeyValues keyValues = KeyValues
            .of(METHOD.withValue(requestAvailable ? request.method() : TAG_VALUE_UNKNOWN),
                    URI.withValue(getUriTag(urlMapper, request)),
                    STATUS.withValue(getStatusMessage(state.response, state.exception)),
                    OUTCOME.withValue(getStatusOutcome(state.response).name()))
            .and(extraTags)
            .and(stream(contextSpecificTags.spliterator(), false)
                .map(contextTag -> contextTag.apply(request, state.response))
                .map(tag -> KeyValue.of(tag.getKey(), tag.getValue()))
                .collect(toList()))
            .and(getRequestTags(request, unknownRequestTags))
            .and(generateTagsForRoute(request));
        if (includeHostTag) {
            keyValues = KeyValues.of(keyValues)
                .and(HOST.withValue(requestAvailable ? request.url().host() : TAG_VALUE_UNKNOWN));
        }
        return keyValues;
    }

    private String getUriTag(Function<Request, String> urlMapper, @Nullable Request request) {
        if (request == null) {
            return TAG_VALUE_UNKNOWN;
        }
        return urlMapper.apply(request);
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

    private Iterable<KeyValue> getRequestTags(@Nullable Request request, Iterable<KeyValue> unknownRequestTags) {
        if (request == null) {
            return unknownRequestTags;
        }
        if (REQUEST_TAG_CLASS_EXISTS) {
            Tags requestTag = request.tag(Tags.class);
            if (requestTag != null) {
                return tagsToKeyValues(requestTag.stream());
            }
            KeyValues keyValues = request.tag(KeyValues.class);
            if (keyValues != null) {
                return keyValues;
            }
        }
        Object requestTag = request.tag();
        if (requestTag instanceof Tags) {
            return tagsToKeyValues(((Tags) requestTag).stream());
        }
        else if (requestTag instanceof KeyValues) {
            return (KeyValues) requestTag;
        }
        return KeyValues.empty();
    }

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

    @Override
    public String getName() {
        return this.metricName;
    }

    @Nullable
    @Override
    public String getContextualName(OkHttpContext context) {
        Request request = context.getOriginalRequest();
        if (request == null) {
            return null;
        }
        return request.method();
    }

}
