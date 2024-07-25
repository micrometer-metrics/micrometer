/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.java11.instrument.binder.jdk;

import io.micrometer.observation.transport.RequestReplySenderContext;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link RequestReplySenderContext} for an {@link HttpClient}.
 *
 * @author Marcin Grzejszczak
 * @since 1.13.0
 */
public class HttpClientContext extends RequestReplySenderContext<HttpRequest.Builder, HttpResponse<?>> {

    private final Function<HttpRequest, String> uriMapper;

    public HttpClientContext(Function<HttpRequest, String> uriMapper) {
        super((carrier, key, value) -> Objects.requireNonNull(carrier).header(key, value));
        this.uriMapper = uriMapper;
    }

    public Function<HttpRequest, String> getUriMapper() {
        return uriMapper;
    }

}
