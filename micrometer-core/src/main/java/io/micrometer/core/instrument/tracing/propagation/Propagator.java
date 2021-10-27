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

package io.micrometer.core.instrument.tracing.propagation;

import java.util.List;

import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.TraceContext;
import io.micrometer.core.lang.Nullable;

/**
 * Inspired by OpenZipkin Brave and OpenTelemetry. Most of the documentation is taken
 * directly from OpenTelemetry.
 *
 * Injects and extracts a value as text into carriers that travel in-band across process
 * boundaries. Encoding is expected to conform to the HTTP Header Field semantics. Values
 * are often encoded as RPC/HTTP request headers.
 *
 * @author OpenZipkin Brave Authors
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface Propagator {

	/**
	 * Collection of headers that contain tracing information.
	 *
	 * @return tracing fields
	 */
	List<String> fields();

	/**
	 * Injects the value downstream, for example as HTTP headers. The carrier may be null
	 * to facilitate calling this method with a lambda for the {@link Setter}, in which
	 * case that null will be passed to the {@link Setter} implementation.
	 *
	 * @param context the {@code Context} containing the value to be injected
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request
	 * @param setter invoked for each propagation key to add or remove
	 * @param <C> carrier of propagation fields, such as an http request
	 */
	<C> void inject(TraceContext context, @Nullable C carrier, Setter<C> setter);

	/**
	 * Extracts the value from upstream. For example, as http headers.
	 *
	 * <p>
	 * If the value could not be parsed, the underlying implementation will decide to set
	 * an object representing either an empty value, an invalid value, or a valid value.
	 * Implementation must not set {@code null}.
	 *
	 * @param carrier holds propagation fields. For example, an outgoing message or http
	 * request
	 * @param getter invoked for each propagation key to get
	 * @param <C> carrier of propagation fields, such as an http request
	 * @return the {@code Context} containing the extracted value
	 */
	<C> Span.Builder extract(C carrier, Getter<C> getter);

	/**
	 * Class that allows a {@code TextMapPropagator} to set propagated fields into a
	 * carrier.
	 *
	 * <p>
	 * {@code Setter} is stateless and allows to be saved as a constant to avoid runtime
	 * allocations.
	 *
	 * @since 6.0.0
	 * @param <C> carrier of propagation fields, such as an http request
	 */
	interface Setter<C> {

		/**
		 * Replaces a propagated field with the given value.
		 *
		 * <p>
		 * For example, a setter for an {@link java.net.HttpURLConnection} would be the
		 * method reference
		 *
		 * {@link java.net.HttpURLConnection#addRequestProperty(String, String)}
		 * @param carrier holds propagation fields. For example, an outgoing message or
		 * http request. To facilitate implementations as java lambdas, this parameter may
		 * be null
		 * @param key the key of the field
		 * @param value the value of the field
		 */
		void set(@Nullable C carrier, String key, String value);

	}

	/**
	 * Interface that allows a {@code TextMapPropagator} to read propagated fields from a
	 * carrier.
	 *
	 * <p>
	 * {@code Getter} is stateless and allows to be saved as a constant to avoid runtime
	 * allocations.
	 *
	 * @param <C> carrier of propagation fields, such as an http request
	 */
	interface Getter<C> {

		/**
		 * Returns the first value of the given propagation {@code key} or returns
		 * {@code null}.
		 *
		 * @param carrier carrier of propagation fields, such as an http request
		 * @param key the key of the field
		 * @return the first value of the given propagation {@code key} or returns
		 * {@code null}
		 */
		@Nullable
		String get(C carrier, String key);

	}

}
