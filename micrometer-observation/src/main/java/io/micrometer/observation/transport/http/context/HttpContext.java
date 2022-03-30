/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.observation.transport.http.context;

import io.micrometer.observation.Observation;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;
import io.micrometer.observation.transport.http.tags.HttpTagsProvider;
import io.micrometer.observation.lang.NonNull;
import io.micrometer.observation.lang.Nullable;

/**
 * {@link Observation.Context} for an HTTP exchange.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 * @param <REQ> request type
 * @param <RES> response type
 */
public abstract class HttpContext<REQ extends HttpRequest, RES extends HttpResponse> extends Observation.Context {

    private final HttpTagsProvider tagsProvider;

    public HttpContext() {
        this(HttpTagsProvider.DEFAULT);
    }

    public HttpContext(HttpTagsProvider tagsProvider) {
        this.tagsProvider = tagsProvider;
    }

    /**
     * Returns the HTTP request.
     *
     * @return request
     */
    @NonNull
    abstract REQ getRequest();

    /**
     * Returns the HTTP response.
     *
     * @return response
     */
    @Nullable
    abstract RES getResponse();

    /**
     * Sets the given HTTP request for this context. Might be {@code null} when an
     * exception occurred and there is no request.
     *
     * @param request HTTP response
     * @return this
     */
    abstract HttpContext<REQ, RES> setRequest(@Nullable REQ request);

    /**
     * Sets the given HTTP response for this context. Might be {@code null} when an
     * exception occurred and there is no response.
     *
     * @param response HTTP response
     * @return this
     */
    abstract HttpContext<REQ, RES> setResponse(@Nullable RES response);

    @NonNull
    @Override
    public KeyValues getLowCardinalityTags() {
        return this.tagsProvider.getLowCardinalityTags(getRequest(), getResponse(), null);
    }
}
