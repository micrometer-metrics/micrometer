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
package io.micrometer.api.instrument.transport.http.context;

import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.instrument.transport.http.tags.HttpTagsProvider;
import io.micrometer.api.lang.NonNull;
import io.micrometer.api.instrument.transport.http.HttpServerRequest;
import io.micrometer.api.instrument.transport.http.HttpServerResponse;

/**
 * {@link Observation.Context Context} for an HTTP server request/response.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class HttpServerContext extends HttpContext<HttpServerRequest, HttpServerResponse> {

    private final HttpServerRequest request;

    private HttpServerResponse response;

    /**
     * Creates a new {@code HttpServerContext}.
     *
     * @param request http server request
     */
    public HttpServerContext(HttpServerRequest request) {
        this.request = request;
    }

    public HttpServerContext(HttpServerRequest request, HttpTagsProvider tagsProvider) {
        super(tagsProvider);
        this.request = request;
    }

    @NonNull
    @Override
    public HttpServerRequest getRequest() {
        return this.request;
    }

    @Override
    public HttpServerResponse getResponse() {
        return this.response;
    }

    @Override
    public HttpServerContext setResponse(HttpServerResponse response) {
        this.response = response;
        return this;
    }

}
