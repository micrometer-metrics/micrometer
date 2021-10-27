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

package io.micrometer.core.instrument.tracing;

/**
 * Allows to customize the current span in scope.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface SpanCustomizer {

	/**
	 * Sets a name on a span.
	 *
	 * @param name name to set on a span
	 * @return this
	 */
	SpanCustomizer name(String name);

	/**
	 * Sets a tag on a span.
	 *
	 * @param key tag key
	 * @param value tag value
	 * @return this
	 */
	SpanCustomizer tag(String key, String value);

	/**
	 * Sets an event on a span.
	 *
	 * @param value event name
	 * @return this
	 */
	SpanCustomizer event(String value);

}
