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
import io.micrometer.core.instrument.tracing.SpanCustomizer;

/**
 * In order to describe your spans via e.g. enums instead of Strings you can use this
 * interface that returns all the characteristics of a span. In Spring Cloud Sleuth we
 * analyze the sources and reuse this information to build a table of known spans, their
 * names, tags and events.
 *
 * We can generate documentation for all created spans but certain requirements need to be
 * met
 *
 * - spans are grouped within an enum - the enum implements the {@link DocumentedSpan}
 * interface - if the span contains {@link TagKey} or {@link EventValue} then those need
 * to be declared as nested enums - the {@link DocumentedSpan#getTagKeys()} and
 * {@link DocumentedSpan#getEvents()} need to call the nested enum's {@code values()}
 * method to retrieve the array of allowed keys / events
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface DocumentedSpan {

	/**
	 * Returns the span name.
	 *
	 * @return span name
	 */
	String getName();

	/**
	 * Returns the allowed tag keys.
	 *
	 * @return allowed tag keys
	 */
	default TagKey[] getTagKeys() {
		return new TagKey[0];
	}

	/**
	 * Returns the allowed events.
	 *
	 * @return allowed events
	 */
	default EventValue[] getEvents() {
		return new EventValue[0];
	}

	/**
	 * Returns required prefix to be there for events and tags. Example {@code foo.} would
	 * require the tags and events to have a {code foo} prefix like this for tags:
	 * {@code foo.bar=true} and {@code foo.started} for events.
	 *
	 * @return required prefix
	 */
	default String prefix() {
		return "";
	}

	/**
	 * Asserts on tags, names and allowed events.
	 *
	 * @param span to wrap
	 * @return wrapped span
	 */
	default AssertingSpan wrap(Span span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpan) {
			return (AssertingSpan) span;
		}
		return AssertingSpan.of(this, span);
	}

	/**
	 * Asserts on tags, names and allowed events.
	 *
	 * @param span to wrap
	 * @return wrapped span
	 */
	default AssertingSpanCustomizer wrap(SpanCustomizer span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanCustomizer) {
			return (AssertingSpanCustomizer) span;
		}
		return AssertingSpanCustomizer.of(this, span);
	}

	/**
	 * Asserts on tags, names and allowed events.
	 *
	 * @param span builder to wrap
	 * @return wrapped span
	 */
	default AssertingSpanBuilder wrap(Span.Builder span) {
		if (span == null) {
			return null;
		}
		else if (span instanceof AssertingSpanBuilder) {
			return (AssertingSpanBuilder) span;
		}
		return AssertingSpanBuilder.of(this, span);
	}

}
