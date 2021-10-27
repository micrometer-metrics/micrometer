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

package io.micrometer.core.instrument.tracing.docs;

import io.micrometer.core.instrument.tracing.SpanCustomizer;

/**
 * A {@link SpanCustomizer} that can perform assertions on itself.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface AssertingSpanCustomizer extends SpanCustomizer {

	/**
	 * Creates an asserting wrapper.
	 *
	 * @param documentedSpan span configuration
	 * @param span span to wrap in assertions
	 * @return asserting span customizer
	 */
	static AssertingSpanCustomizer of(DocumentedSpan documentedSpan, SpanCustomizer span) {
		if (span instanceof AssertingSpanCustomizer) {
			return (AssertingSpanCustomizer) span;
		}
		return new ImmutableAssertingSpanCustomizer(documentedSpan, span);
	}

	/**
	 * Returns the underlying delegate. Used when casting is necessary.
	 *
	 * @param span span to check for wrapping
	 * @param <T> type extending a span
	 * @return unwrapped object
	 */
	@SuppressWarnings("unchecked")
	static <T extends SpanCustomizer> T unwrap(SpanCustomizer span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanCustomizer) {
			return (T) ((AssertingSpanCustomizer) span).getDelegate();
		}
		return (T) span;
	}

	/**
	 * Returns a documented span.
	 *
	 * @return a {@link DocumentedSpan} with span configuration
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * Returns the delegate.
	 *
	 * @return wrapped {@link SpanCustomizer}
	 */
	SpanCustomizer getDelegate();

	@Override
	default AssertingSpanCustomizer tag(String key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key, value);
		return this;
	}

	/**
	 * Sets a tag on a span.
	 *
	 * @param key tag key
	 * @param value tag
	 * @return this
	 */
	default AssertingSpanCustomizer tag(TagKey key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpanCustomizer event(String value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value);
		return this;
	}

	/**
	 * Sets an event on a span.
	 *
	 * @param value event
	 * @return this
	 */
	default AssertingSpanCustomizer event(EventValue value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpanCustomizer name(String name) {
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		getDelegate().name(name);
		return this;
	}

}
