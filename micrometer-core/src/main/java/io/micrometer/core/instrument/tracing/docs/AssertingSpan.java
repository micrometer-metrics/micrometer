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

import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.TraceContext;

/**
 * {@link Span} that performs additional assertions such as allowed name, tag, event
 * verification and upon reporting, whether the span had been started in the first place.
 *
 * You need to turn on assertions via system properties or environment variables to start
 * breaking your tests or production code. Check {@link DocumentedSpanAssertions} for more
 * information.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface AssertingSpan extends Span {

	/**
	 * Wraps a span in an asserting wrapper.
	 *
	 * @param documentedSpan span configuration
	 * @param span span to wrap in assertions
	 * @return asserting span
	 */
	static AssertingSpan of(DocumentedSpan documentedSpan, Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (AssertingSpan) span;
		}
		return new ImmutableAssertingSpan(documentedSpan, span);
	}

	/**
	 * Returns the underlying delegate. Used when casting is necessary.
	 *
	 * @param span span to check for wrapping
	 * @param <T> type extending a span
	 * @return unwrapped object
	 */
	@SuppressWarnings("unchecked")
	static <T extends Span> T unwrap(Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (T) ((AssertingSpan) span).getDelegate();
		}
		return (T) span;
	}

	/**
	 * Returns a {@link DocumentedSpan}.
	 *
	 * @return a {@link DocumentedSpan}.
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * Returns the delegate.
	 *
	 * @return wrapped {@link Span}.
	 */
	Span getDelegate();

	/**
	 * Determines whether the span was started.
	 *
	 * @return {@code true} when this span was started
	 */
	default boolean isStarted() {
		return false;
	}

	@Override
	default AssertingSpan tag(String key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key, value);
		return this;
	}

	/**
	 * Tags a span via {@link TagKey}.
	 *
	 * @param key tag key
	 * @param value tag value
	 * @return this
	 */
	default AssertingSpan tag(TagKey key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpan event(String value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value);
		return this;
	}

	@Override
	default AssertingSpan event(long micros, String value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(micros, value);
		return this;
	}

	/**
	 * Annotates with an event via {@link EventValue}.
	 *
	 * @param value event value
	 * @return this
	 */
	default AssertingSpan event(EventValue value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpan name(String name) {
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		getDelegate().name(name);
		return this;
	}

	@Override
	default boolean isNoop() {
		return getDelegate().isNoop();
	}

	@Override
	default TraceContext context() {
		return getDelegate().context();
	}

	@Override
	default AssertingSpan start() {
		getDelegate().start();
		return this;
	}

	@Override
	default Span start(long micros) {
		getDelegate().start(micros);
		return this;
	}

	@Override
	default AssertingSpan error(Throwable throwable) {
		getDelegate().error(throwable);
		return this;
	}

	@Override
	default void end() {
		DocumentedSpanAssertions.assertThatSpanStartedBeforeEnd(this);
		getDelegate().end();
	}

	@Override
	default void end(long micros) {
		DocumentedSpanAssertions.assertThatSpanStartedBeforeEnd(this);
		getDelegate().end(micros);
	}

	@Override
	default void abandon() {
		getDelegate().abandon();
	}

	@Override
	default AssertingSpan remoteServiceName(String remoteServiceName) {
		getDelegate().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	default Span remoteIpAndPort(String ip, int port) {
		getDelegate().remoteIpAndPort(ip, port);
		return this;
	}

}
