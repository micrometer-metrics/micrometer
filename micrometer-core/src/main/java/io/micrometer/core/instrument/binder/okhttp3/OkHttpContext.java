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
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.observation.transport.SenderContext;
import okhttp3.Request;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link SenderContext} for OkHttp3.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class OkHttpContext extends RequestReplySenderContext<Request.Builder, Response>
        implements Supplier<OkHttpContext> {

    private final Function<Request, String> urlMapper;

    private final Iterable<KeyValue> extraTags;

    private final Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags;

    private final boolean includeHostTag;

    private final Request request;

    private OkHttpObservationInterceptor.CallState state;

    public OkHttpContext(Function<Request, String> urlMapper, Iterable<KeyValue> extraTags,
            Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags, boolean includeHostTag,
            Request request) {
        super(OkHttpContext::setHeader, Kind.CLIENT);
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.includeHostTag = includeHostTag;
        this.request = request;
        this.state = new OkHttpObservationInterceptor.CallState(request);
        this.setCarrier(request.newBuilder());
    }

    private static void setHeader(Request.@Nullable Builder builder, String key, String value) {
        if (builder != null) {
            builder.header(key, value);
        }
    }

    /**
     * @deprecated please use other constructor(s).
     */
    @Deprecated
    public OkHttpContext(Function<Request, String> urlMapper, Iterable<KeyValue> extraTags,
            Iterable<BiFunction<Request, @Nullable Response, KeyValue>> contextSpecificTags, Iterable<KeyValue> ignored,
            boolean includeHostTag, Request request) {
        this(urlMapper, extraTags, contextSpecificTags, includeHostTag, request);
    }

    void setState(OkHttpObservationInterceptor.CallState state) {
        this.state = state;
    }

    OkHttpObservationInterceptor.CallState getState() {
        return state;
    }

    public Function<Request, String> getUrlMapper() {
        return urlMapper;
    }

    public Iterable<KeyValue> getExtraTags() {
        return extraTags;
    }

    public Iterable<BiFunction<Request, @Nullable Response, KeyValue>> getContextSpecificTags() {
        return contextSpecificTags;
    }

    /**
     * @deprecated The request cannot be null according to the OkHttp API
     */
    @Deprecated
    public Iterable<KeyValue> getUnknownRequestTags() {
        return KeyValues.empty();
    }

    public boolean isIncludeHostTag() {
        return includeHostTag;
    }

    public Request getOriginalRequest() {
        return request;
    }

    @Override
    public OkHttpContext get() {
        return this;
    }

    @Override
    public Request.Builder getCarrier() {
        return Objects.requireNonNull(super.getCarrier());
    }

}
