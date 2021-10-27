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

import io.micrometer.core.instrument.tracing.SpanCustomizer;
import io.micrometer.core.instrument.tracing.TraceContext;
import io.micrometer.core.instrument.transport.http.HttpRequest;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Use this to control the request data recorded.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface HttpRequestParser {

	/**
	 * Implement to choose what data from the http request are parsed into the span
	 * representing it.
	 *
	 * @param request current request
	 * @param context corresponding trace context
	 * @param span customizer for the current span
	 */
	void parse(HttpRequest request, TraceContext context, SpanCustomizer span);

}
