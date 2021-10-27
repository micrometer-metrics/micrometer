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
 * Represents the "current span" until {@link ScopedSpan#end()} ()} is called.
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface ScopedSpan {

	/**
	 * Decides whether span is noop.
	 *
	 * @return {@code true} when no recording is done and nothing is reported to an
	 * external system. However, this span should still be injected into outgoing
	 * requests. Use this flag to avoid performing expensive computation
	 */
	boolean isNoop();

	/**
	 * Returns the {@link TraceContext}.
	 *
	 * @return {@link TraceContext} corresponding to this span.
	 */
	TraceContext context();

	/**
	 * Sets a name on this span.
	 *
	 * @param name name to set on the span
	 * @return this
	 */
	ScopedSpan name(String name);

	/**
	 * Sets a tag on this span.
	 *
	 * @param key tag key
	 * @param value tag value
	 * @return this
	 */
	ScopedSpan tag(String key, String value);

	/**
	 * Sets an event on this span.
	 *
	 * @param value event name to set on the span
	 * @return this
	 */
	ScopedSpan event(String value);

	/**
	 * Records an exception for this span.
	 *
	 * @param throwable to record
	 * @return this
	 */
	ScopedSpan error(Throwable throwable);

	/**
	 * Ends the span. The span gets stopped and recorded if not noop.
	 */
	void end();

}
