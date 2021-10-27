/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.tracing.http;

import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.TraceContext;
import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;
import io.micrometer.core.lang.Nullable;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * This standardizes a way to instrument http clients, particularly in a way that
 * encourages use of portable customizations via {@link HttpRequestParser} and
 * {@link HttpResponseParser}.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface HttpClientHandler {

	/**
	 * Starts the client span after assigning it a name and tags. This injects the trace
	 * context onto the request before returning.
	 *
	 * Call this before sending the request on the wire.
	 *
	 * @param request to inject the tracing context with
	 * @return client side span
	 */
	Span handleSend(HttpClientRequest request);

	/**
	 * Same as {@link #handleSend(HttpClientRequest)} but with an explicit parent
	 * {@link TraceContext}.
	 *
	 * @param request to inject the tracing context with
	 * @param parent {@link TraceContext} that is to be the client side span's parent
	 * @return client side span
	 */
	Span handleSend(HttpClientRequest request, @Nullable TraceContext parent);

	/**
	 * Finishes the client span after assigning it tags according to the response or
	 * error.
	 *
	 * @param response the HTTP response
	 * @param span span to be ended
	 */
	void handleReceive(HttpClientResponse response, Span span);

}
