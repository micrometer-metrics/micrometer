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

import io.micrometer.core.lang.Nullable;

/**
 * Contains trace and span data.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface TraceContext {

	/**
	 * Returns the trace id.
	 *
	 * @return trace id of a span
	 */
	String traceId();

	/**
	 * Returns the parent span id.
	 *
	 * @return parent span id or {@code null} if one is not set
	 */
	@Nullable
	String parentId();

	/**
	 * Returns the span id.
	 *
	 * @return span id
	 */
	String spanId();

	/**
	 * Decides whether the span is sampled.
	 *
	 * @return {@code true} when sampled, {@code false} when not sampled and {@code null}
	 * when sampling decision should be deferred
	 */
	Boolean sampled();

	/**
	 * Builder for {@link TraceContext}.
	 *
	 * @since 6.0.0
	 */
	interface Builder {

		/**
		 * Sets trace id on the trace context.
		 *
		 * @param traceId trace id
		 * @return this
		 */
		Builder traceId(String traceId);

		/**
		 * Sets parent id on the trace context.
		 *
		 * @param parentId parent trace id
		 * @return this
		 */
		Builder parentId(String parentId);

		/**
		 * Sets span id on the trace context.
		 *
		 * @param spanId span id
		 * @return this
		 */
		Builder spanId(String spanId);

		/**
		 * Sets sampled on the trace context.
		 *
		 * @param sampled if span is sampled
		 * @return this
		 */
		Builder sampled(Boolean sampled);

		/**
		 * Builds the trace context.
		 *
		 * @return trace context
		 */
		TraceContext build();

	}

}
