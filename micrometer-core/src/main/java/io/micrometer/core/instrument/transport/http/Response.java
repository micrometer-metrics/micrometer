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

import java.util.Collection;

import io.micrometer.core.instrument.transport.Kind;
import io.micrometer.core.lang.Nullable;

/**
 * This API is taken from OpenZipkin Brave.
 *
 * Abstract response type used for parsing and sampling.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface Response {

	/**
	 * Returns the header names.
	 *
	 * @return list of header names
	 */
	Collection<String> headerNames();

	/**
	 * Returns the HTTP request.
	 *
	 * @return corresponding request
	 */
	@Nullable
	Request request();

	/**
	 * Returns the exception.
	 *
	 * @return exception that occurred or {@code null} if there was none.
	 */
	@Nullable
	Throwable error();

	/**
	 * Returns the underlying response object.
	 *
	 * @return the underlying response object or {@code null} if there is none.
	 */
	Object unwrap();

	/**
	 * Returns the transport kind.
	 *
	 * @return the remote kind describing the direction and type of the request
	 */
	Kind kind();

}
