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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A call interceptor for OkHttp3.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class OkHttpObservationInterceptor implements Interceptor {

    private final ObservationRegistry registry;

    private @Nullable OkHttpObservationConvention observationConvention;

    private final String requestMetricName;

    private final Function<Request, String> urlMapper;

    private final Iterable<KeyValue> extraTags;

    private final Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags;

    private final boolean includeHostTag;

    public OkHttpObservationInterceptor(ObservationRegistry registry,
            @Nullable OkHttpObservationConvention observationConvention, String requestsMetricName,
            Function<Request, String> urlMapper, Iterable<KeyValue> extraTags,
            Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags, boolean includeHostTag) {
        this.registry = registry;
        this.observationConvention = observationConvention;
        this.requestMetricName = requestsMetricName;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.includeHostTag = includeHostTag;
    }

    /**
     * @deprecated Please use other ctor(s).
     */
    @Deprecated
    public OkHttpObservationInterceptor(ObservationRegistry registry,
            @Nullable OkHttpObservationConvention observationConvention, String requestsMetricName,
            Function<Request, String> urlMapper, Iterable<KeyValue> extraTags,
            Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags, Iterable<String> ignored,
            boolean includeHostTag) {
        this(registry, observationConvention, requestsMetricName, urlMapper, extraTags, contextSpecificTags,
                includeHostTag);
    }

    public static OkHttpObservationInterceptor.Builder builder(ObservationRegistry registry, String name) {
        return new OkHttpObservationInterceptor.Builder(registry, name);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        OkHttpContext okHttpContext = new OkHttpContext(this.urlMapper, this.extraTags, this.contextSpecificTags,
                this.includeHostTag, chain.request());
        Observation observation = OkHttpObservationDocumentation.DEFAULT
            .observation(this.observationConvention, new DefaultOkHttpObservationConvention(requestMetricName),
                    okHttpContext, this.registry)
            .start();
        try {
            Response response = chain.proceed(okHttpContext.rebuildAndGetRequest());
            okHttpContext.setResponse(response);
            return response;
        }
        catch (IOException ex) {
            observation.error(ex);
            throw ex;
        }
        finally {
            observation.stop();
        }
    }

    public void setObservationConvention(OkHttpObservationConvention observationConvention) {
        this.observationConvention = observationConvention;
    }

    public static class Builder {

        /**
         * Header name for URI patterns which will be used for tag values.
         */
        public static final String URI_PATTERN = "URI_PATTERN";

        private final String name;

        private final ObservationRegistry registry;

        private Function<Request, String> uriMapper = (request) -> Optional.ofNullable(request.header(URI_PATTERN))
            .orElse(KeyValue.NONE_VALUE);

        private KeyValues tags = KeyValues.empty();

        private final Collection<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags = new ArrayList<>();

        private boolean includeHostTag = true;

        private @Nullable OkHttpObservationConvention observationConvention;

        Builder(ObservationRegistry registry, String name) {
            this.registry = registry;
            this.name = name;
        }

        public OkHttpObservationInterceptor.Builder tags(Iterable<KeyValue> tags) {
            this.tags = this.tags.and(tags);
            return this;
        }

        public OkHttpObservationInterceptor.Builder observationConvention(
                OkHttpObservationConvention observationConvention) {
            this.observationConvention = observationConvention;
            return this;
        }

        /**
         * Add a {@link KeyValue} to any already configured tags on this Builder.
         * @param tag tag to add
         * @return this builder
         */
        public OkHttpObservationInterceptor.Builder tag(KeyValue tag) {
            this.tags = this.tags.and(tag);
            return this;
        }

        /**
         * Add a context-specific tag.
         * @param contextSpecificTag function to create a context-specific tag
         * @return this builder
         */
        public OkHttpObservationInterceptor.Builder tag(
                BiFunction<Request, @Nullable Response, KeyValue> contextSpecificTag) {
            this.contextSpecificTags.add(contextSpecificTag);
            return this;
        }

        public OkHttpObservationInterceptor.Builder uriMapper(Function<Request, String> uriMapper) {
            this.uriMapper = uriMapper;
            return this;
        }

        /**
         * Historically, OkHttp Metrics provided by {@link OkHttpObservationInterceptor}
         * included a {@code host} tag for the target host being called. To align with
         * other HTTP client metrics, this was changed to {@code target.host}, but to
         * maintain backwards compatibility the {@code host} tag can also be included. By
         * default, {@code includeHostTag} is {@literal true} so both tags are included.
         * @param includeHostTag whether to include the {@code host} tag
         * @return this builder
         */
        public OkHttpObservationInterceptor.Builder includeHostTag(boolean includeHostTag) {
            this.includeHostTag = includeHostTag;
            return this;
        }

        /**
         * KeyValue keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         * <p>
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @return this builder
         * @deprecated The request cannot be null according to the OkHttp API
         */
        @Deprecated
        public OkHttpObservationInterceptor.Builder requestTagKeys(String... ignored) {
            return this;
        }

        /**
         * KeyValue keys for {@link Request#tag()} or {@link Request#tag(Class)}.
         * <p>
         * These keys will be added with {@literal UNKNOWN} values when {@link Request} is
         * {@literal null}. Note that this is required only for Prometheus as it requires
         * tag match for the same metric.
         * @return this builder
         * @deprecated The request cannot be null according to the OkHttp API
         */
        @Deprecated
        public OkHttpObservationInterceptor.Builder requestTagKeys(Iterable<String> ignored) {
            return this;
        }

        public OkHttpObservationInterceptor build() {
            return new OkHttpObservationInterceptor(registry, observationConvention, name, uriMapper, tags,
                    contextSpecificTags, includeHostTag);
        }

    }

}
