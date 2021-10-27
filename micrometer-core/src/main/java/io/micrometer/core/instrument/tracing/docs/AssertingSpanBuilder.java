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
 * A {@link Span.Builder} that can perform assertions on itself.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface AssertingSpanBuilder extends Span.Builder {

	/**
	 * Creates an asserting wrapper.
	 *
	 * @param documentedSpan span configuration
	 * @param builder builder to wrap in assertions
	 * @return asserting span builder
	 */
	static AssertingSpanBuilder of(DocumentedSpan documentedSpan, Span.Builder builder) {
		if (builder == null) {
			return null;
		}
		else if (builder instanceof AssertingSpanBuilder) {
			return (AssertingSpanBuilder) builder;
		}
		return new ImmutableAssertingSpanBuilder(documentedSpan, builder);
	}

	/**
	 * Returns a {@link DocumentedSpan} with span configuration.
	 *
	 * @return a {@link DocumentedSpan}
	 */
	DocumentedSpan getDocumentedSpan();

	/**
	 * The delegate.
	 *
	 * @return wrapped {@link Span.Builder}
	 */
	Span.Builder getDelegate();

	@Override
	default AssertingSpanBuilder tag(String key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key, value);
		return this;
	}

	/**
	 * Sets a tag on a span.
	 * @param key tag key
	 * @param value tag
	 * @return this
	 */
	default AssertingSpanBuilder tag(TagKey key, String value) {
		DocumentedSpanAssertions.assertThatKeyIsValid(key, getDocumentedSpan());
		getDelegate().tag(key.getKey(), value);
		return this;
	}

	@Override
	default AssertingSpanBuilder event(String value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value);
		return this;
	}

	/**
	 * Sets an event on a span.
	 * @param value event
	 * @return this
	 */
	default AssertingSpanBuilder event(EventValue value) {
		DocumentedSpanAssertions.assertThatEventIsValid(value, getDocumentedSpan());
		getDelegate().event(value.getValue());
		return this;
	}

	@Override
	default AssertingSpanBuilder name(String name) {
		DocumentedSpanAssertions.assertThatNameIsValid(name, getDocumentedSpan());
		getDelegate().name(name);
		return this;
	}

	@Override
	default AssertingSpanBuilder error(Throwable throwable) {
		getDelegate().error(throwable);
		return this;
	}

	@Override
	default AssertingSpanBuilder remoteServiceName(String remoteServiceName) {
		getDelegate().remoteServiceName(remoteServiceName);
		return this;
	}

	@Override
	default Span.Builder remoteIpAndPort(String ip, int port) {
		getDelegate().remoteIpAndPort(ip, port);
		return this;
	}

	@Override
	default AssertingSpanBuilder setParent(TraceContext context) {
		getDelegate().setParent(context);
		return this;
	}

	@Override
	default AssertingSpanBuilder setNoParent() {
		getDelegate().setNoParent();
		return this;
	}

	@Override
	default AssertingSpanBuilder kind(Span.Kind spanKind) {
		getDelegate().kind(spanKind);
		return this;
	}

	@Override
	default AssertingSpan start() {
		Span span = getDelegate().start();
		return assertingDocumentedSpan(span);
	}

	/**
	 * Converts a span to an asserting span for the given documented span.
	 * @param span span to convert
	 * @return asserting span
	 */
	default AssertingSpan assertingDocumentedSpan(Span span) {
		DocumentedSpan documentedSpan = getDocumentedSpan();
		return new AssertingSpan() {
			@Override
			public DocumentedSpan getDocumentedSpan() {
				return documentedSpan;
			}

			@Override
			public Span getDelegate() {
				return span;
			}

			@Override
			public boolean isStarted() {
				return true;
			}

			@Override
			public String toString() {
				return getDelegate().toString();
			}
		};
	}

	@Override
	default Span start(long micros) {
		Span span = getDelegate().start(micros);
		return assertingDocumentedSpan(span);
	}

}
