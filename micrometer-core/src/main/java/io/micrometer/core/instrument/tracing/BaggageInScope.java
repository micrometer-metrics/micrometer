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

import java.io.Closeable;

import io.micrometer.core.lang.Nullable;

/**
 * Inspired by OpenZipkin Brave's {@code BaggageField}. Since some tracer implementations
 * require a scope to be wrapped around baggage, baggage must be closed so that the scope
 * does not leak. Some tracer implementations make baggage immutable (e.g. OpenTelemetry),
 * so when the value gets updated they might create new scope (others will return the same
 * one - e.g. OpenZipkin Brave).
 *
 * Represents a single baggage entry.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface BaggageInScope extends Closeable {

	/**
	 * Returns the baggage name.
	 *
	 * @return name of the baggage entry
	 */
	String name();

	/**
	 * Returns the baggage value.
	 *
	 * @return value of the baggage entry or {@code null} if not set
	 */
	@Nullable
	String get();

	/**
	 * Retrieves baggage from the given {@link TraceContext}.
	 *
	 * @param traceContext context containing baggage
	 * @return value of the baggage entry or {@code null} if not set
	 */
	@Nullable
	String get(TraceContext traceContext);

	/**
	 * Sets the baggage value.
	 *
	 * @param value to set
	 * @return new scope
	 */
	BaggageInScope set(String value);

	/**
	 * Sets the baggage value for the given {@link TraceContext}.
	 *
	 * @param traceContext context containing baggage
	 * @param value to set
	 * @return new scope
	 */
	BaggageInScope set(TraceContext traceContext, String value);

	/**
	 * Sets the current baggage in scope.
	 *
	 * @return this in scope
	 */
	BaggageInScope makeCurrent();

	@Override
	void close();

}
