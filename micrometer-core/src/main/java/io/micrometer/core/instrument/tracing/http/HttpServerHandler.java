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
import io.micrometer.core.instrument.transport.http.HttpServerRequest;
import io.micrometer.core.instrument.transport.http.HttpServerResponse;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * This standardizes a way to instrument http servers, particularly in a way that
 * encourages use of portable customizations via {@link HttpRequestParser} and
 * {@link HttpResponseParser}.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface HttpServerHandler {

	/**
	 * Conditionally joins a span, or starts a new trace, depending on if a trace context
	 * was extracted from the request. Tags are added before the span is started.
	 *
	 * @param request an HTTP request
	 * @return server side span (either joined or a new trace)
	 */
	Span handleReceive(HttpServerRequest request);

	/**
	 * Finishes the server span after assigning it tags according to the response or
	 * error.
	 *
	 * @param response an HTTP response
	 * @param span server side span to end
	 */
	void handleSend(HttpServerResponse response, Span span);

}
