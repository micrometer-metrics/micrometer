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

import java.util.Objects;

import io.micrometer.core.instrument.tracing.SpanCustomizer;

import static java.util.Objects.requireNonNull;

class ImmutableAssertingSpanCustomizer implements AssertingSpanCustomizer {

	private final DocumentedSpan documentedSpan;

	private final SpanCustomizer delegate;

	ImmutableAssertingSpanCustomizer(DocumentedSpan documentedSpan, SpanCustomizer delegate) {
		requireNonNull(documentedSpan);
		requireNonNull(delegate);
		this.documentedSpan = documentedSpan;
		this.delegate = delegate;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ImmutableAssertingSpanCustomizer that = (ImmutableAssertingSpanCustomizer) o;
		return Objects.equals(this.documentedSpan, that.documentedSpan) && Objects.equals(this.delegate, that.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.documentedSpan, this.delegate);
	}

	@Override
	public DocumentedSpan getDocumentedSpan() {
		return this.documentedSpan;
	}

	@Override
	public SpanCustomizer getDelegate() {
		return this.delegate;
	}

}
