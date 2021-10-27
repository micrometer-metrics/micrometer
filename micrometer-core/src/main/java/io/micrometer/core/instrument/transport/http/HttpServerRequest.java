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

package io.micrometer.core.instrument.transport.http;

import io.micrometer.core.instrument.transport.Kind;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract request type used for parsing and sampling. Represents an HTTP Server request.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface HttpServerRequest extends HttpRequest {

	/**
	 * Returns an HTTP attribute.
	 *
	 * @param key attribute key
	 * @return attribute with the given key or {@code null} if not set
	 */
	default Object getAttribute(String key) {
		return null;
	}

	/**
	 * Sets an HTTP attribute.
	 *
	 * @param key attribute key
	 * @param value attribute value
	 */
	default void setAttribute(String key, Object value) {

	}

	@Override
	default Kind kind() {
		return Kind.SERVER;
	}

}
