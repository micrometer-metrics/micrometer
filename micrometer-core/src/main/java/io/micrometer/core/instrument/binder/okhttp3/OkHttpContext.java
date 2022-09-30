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
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.RequestReplySenderContext;
import okhttp3.Request;
import okhttp3.Response;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A {@link SenderContext} for OkHttp3.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
@SuppressWarnings("jol")
public class OkHttpContext extends RequestReplySenderContext<Request.Builder, Response>
        implements Supplier<OkHttpContext> {

    private final Function<Request, String> urlMapper;

    private final Iterable<KeyValue> extraTags;

    private final Iterable<BiFunction<Request, Response, KeyValue>> contextSpecificTags;

    private final Iterable<KeyValue> unknownRequestTags;

    private final boolean includeHostTag;

    private final Request originalRequest;

    private OkHttpObservationInterceptor.CallState state;

    public OkHttpContext(Function<Request, String> urlMapper, Iterable<KeyValue> extraTags,
            Iterable<BiFunction<Request, Response, KeyValue>> contextSpecificTags,
            Iterable<KeyValue> unknownRequestTags, boolean includeHostTag, Request originalRequest) {
        super((carrier, key, value) -> {
            if (carrier != null) {
                carrier.header(key, value);
            }
        }, Kind.CLIENT);
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.unknownRequestTags = unknownRequestTags;
        this.includeHostTag = includeHostTag;
        this.originalRequest = originalRequest;
    }

    public void setState(OkHttpObservationInterceptor.CallState state) {
        this.state = state;
    }

    public OkHttpObservationInterceptor.CallState getState() {
        return state;
    }

    public Function<Request, String> getUrlMapper() {
        return urlMapper;
    }

    public Iterable<KeyValue> getExtraTags() {
        return extraTags;
    }

    public Iterable<BiFunction<Request, Response, KeyValue>> getContextSpecificTags() {
        return contextSpecificTags;
    }

    public Iterable<KeyValue> getUnknownRequestTags() {
        return unknownRequestTags;
    }

    public boolean isIncludeHostTag() {
        return includeHostTag;
    }

    public Request getOriginalRequest() {
        return originalRequest;
    }

    @Override
    public OkHttpContext get() {
        return this;
    }

}
