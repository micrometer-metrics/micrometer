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

package io.micrometer.core.instrument.tracing.exporter;

import java.util.Collection;
import java.util.Map;

import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.lang.Nullable;

/**
 * This API is inspired by OpenZipkin Brave (from {code MutableSpan}).
 *
 * Represents a span that has been finished and is ready to be sent to an external
 * location (e.g. Zipkin).
 *
 * @author OpenZipkin Brave Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface FinishedSpan {

	/**
	 * Returns the span name.
	 *
	 * @return name
	 */
	String getName();

	/**
	 * Returns the span's start timestamp.
	 *
	 * @return start timestamp
	 */
	long getStartTimestamp();

	/**
	 * Returns the span's end timestamp.
	 *
	 * @return end timestamp
	 */
	long getEndTimestamp();

	/**
	 * Returns the span tags.
	 *
	 * @return tags
	 */
	Map<String, String> getTags();

	/**
	 * Returns the span events.
	 *
	 * @return span's events as timestamp to value mapping
	 */
	Collection<Map.Entry<Long, String>> getEvents();

	/**
	 * Returns the span id.
	 *
	 * @return span's span id
	 */
	String getSpanId();

	/**
	 * Returns the span's parent id.
	 *
	 * @return span's parent id or {@code null} if not set
	 */
	@Nullable
	String getParentId();

	/**
	 * Returns the span's remote ip.
	 *
	 * @return span's remote ip
	 */
	@Nullable
	String getRemoteIp();

	/**
	 * Returns the span's local ip.
	 *
	 * @return span's local ip
	 */
	@Nullable
	default String getLocalIp() {
		return null;
	}

	/**
	 * Returns the span's remote port.
	 *
	 * @return span's remote port
	 */
	int getRemotePort();

	/**
	 * Returns the span's trace id.
	 *
	 * @return span's trace id
	 */
	String getTraceId();

	/**
	 * Returns the error.
	 *
	 * @return corresponding error or {@code null} if one was not thrown
	 */
	@Nullable
	Throwable getError();

	/**
	 * Returns the span's kind.
	 *
	 * @return span's kind
	 */
	Span.Kind getKind();

	/**
	 * Returns the remote service name.
	 *
	 * @return remote service name or {@code null} if not set
	 */
	@Nullable
	String getRemoteServiceName();

}
