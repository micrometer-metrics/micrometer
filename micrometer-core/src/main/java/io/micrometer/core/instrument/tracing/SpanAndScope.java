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

/**
 * Container object for {@link Span} and its corresponding {@link Tracer.SpanInScope}.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class SpanAndScope implements Closeable {

	private final Span span;

	private final Tracer.SpanInScope scope;

	/**
	 * Creates a new instance of {@link SpanAndScope}.
	 *
	 * @param span span
	 * @param scope scope of the span
	 */
	public SpanAndScope(Span span, Tracer.SpanInScope scope) {
		this.span = span;
		this.scope = scope;
	}

	/**
	 * Returns the span.
	 *
	 * @return stored span
	 */
	public Span getSpan() {
		return this.span;
	}

	/**
	 * Returns the span in scope.
	 *
	 * @return stored scope of the span
	 */
	public Tracer.SpanInScope getScope() {
		return this.scope;
	}

	@Override
	public String toString() {
		return "SpanAndScope{" + "span=" + this.span + '}';
	}

	@Override
	public void close() {
		this.scope.close();
		this.span.end();
	}

}
