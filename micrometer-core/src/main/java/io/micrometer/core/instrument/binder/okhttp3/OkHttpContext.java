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

import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.transport.http.context.HttpClientContext;
import okhttp3.Request;
import okhttp3.Response;

import java.util.function.BiFunction;
import java.util.function.Function;

public class OkHttpContext extends HttpClientContext {
    private final OkHttpMetricsEventListener.CallState state;
    private final Function<Request, String> urlMapper;
    private final Iterable<Tag> extraTags;
    private final Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags;
    private final Iterable<Tag> unknownRequestTags;
    private final boolean includeHostTag;

    public OkHttpContext(OkHttpMetricsEventListener.CallState state, Function<Request, String> urlMapper, Iterable<Tag> extraTags, Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags, Iterable<Tag> unknownRequestTags, boolean includeHostTag) {
        this.state = state;
        this.urlMapper = urlMapper;
        this.extraTags = extraTags;
        this.contextSpecificTags = contextSpecificTags;
        this.unknownRequestTags = unknownRequestTags;
        this.includeHostTag = includeHostTag;
    }

    public OkHttpMetricsEventListener.CallState getState() {
        return state;
    }

    public Function<Request, String> getUrlMapper() {
        return urlMapper;
    }

    public Iterable<Tag> getExtraTags() {
        return extraTags;
    }

    public Iterable<BiFunction<Request, Response, Tag>> getContextSpecificTags() {
        return contextSpecificTags;
    }

    public Iterable<Tag> getUnknownRequestTags() {
        return unknownRequestTags;
    }

    public boolean isIncludeHostTag() {
        return includeHostTag;
    }
}
