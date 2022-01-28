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

import io.micrometer.api.instrument.Observation;
import io.micrometer.api.instrument.transport.http.tags.HttpTagsProvider;
import io.micrometer.api.instrument.transport.http.HttpClientRequest;
import io.micrometer.api.instrument.transport.http.HttpClientResponse;
import io.micrometer.api.lang.NonNull;

/**
 * {@link Observation.Context Context}
 * for an HTTP client request/response.
 *
 * @author Jonatan Ivanov
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class HttpClientHandlerContext extends HttpHandlerContext<HttpClientRequest, HttpClientResponse> {

    private final HttpClientRequest request;

    private HttpClientResponse response;

    /**
     * Creates a new {@code HttpClientHandlerContext}.
     *
     * @param request http client request
     */
    public HttpClientHandlerContext(HttpClientRequest request) {
        this.request = request;
    }

    public HttpClientHandlerContext(HttpClientRequest request, HttpTagsProvider tagsProvider) {
        super(tagsProvider);
        this.request = request;
    }

    @NonNull
    @Override
    public HttpClientRequest getRequest() {
        return this.request;
    }

    @Override
    public HttpClientResponse getResponse() {
        return this.response;
    }

    @Override
    public HttpClientHandlerContext setResponse(HttpClientResponse response) {
        this.response = response;
        return this;
    }

}
