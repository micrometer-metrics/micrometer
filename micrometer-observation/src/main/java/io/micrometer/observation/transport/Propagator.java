/*
 * Copyright 2022 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.observation.transport;

import org.jspecify.annotations.Nullable;

import java.util.Collections;

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
 * @since 1.10.0
 */
public interface Propagator {

    /**
     * Class that allows to set propagated fields into a carrier.
     *
     * <p>
     * {@code Setter} is stateless and allows to be saved as a constant to avoid runtime
     * allocations.
     *
     * @param <C> carrier of propagation fields, such as an http request
     * @since 1.10.0
     */
    interface Setter<C> {

        /**
         * Replaces a propagated field with the given value.
         *
         * <p>
         * For example, a setter for an {@link java.net.HttpURLConnection} would be the
         * method reference
         * {@link java.net.HttpURLConnection#addRequestProperty(String, String)}
         * @param carrier holds propagation fields. For example, an outgoing message or
         * http request. To facilitate implementations as java lambdas, this parameter may
         * be null.
         * @param key the key of the field.
         * @param value the value of the field.
         */
        void set(@Nullable C carrier, String key, String value);

    }

    /**
     * Interface that allows to read propagated fields from a carrier.
     *
     * <p>
     * {@code Getter} is stateless and allows to be saved as a constant to avoid runtime
     * allocations.
     *
     * @param <C> carrier of propagation fields, such as an http request.
     * @since 1.10.0
     */
    interface Getter<C> {

        /**
         * Returns the first value of the given propagation {@code key} or returns
         * {@code null}. This method should be preferred over
         * {@link #getAll(Object, String)}} for better performance in cases where it is
         * expected that the key is not repeated.
         * @param carrier carrier of propagation fields, such as an http request.
         * @param key the key of the field.
         * @return the first value of the given propagation {@code key} or returns
         * {@code null}.
         */
        @Nullable String get(C carrier, String key);

        /**
         * Get all values of the given propagation {@code key}, if any exist. This should
         * only be used when it is expected that the key may be repeated.
         * {@link #get(Object, String)} should be preferred in other cases for
         * performance.
         * @param carrier carrier of propagation fields, such as an http request.
         * @param key the key of the field.
         * @return all values of the given propagation {@code key} or returns an empty
         * {@code Iterable} if no values are found.
         * @implNote For backward-compatibility, a default implementation is provided that
         * returns a list with the value of {@link #get(Object, String)} or an empty list
         * if no values are found. Implementors of this interface should override this
         * method to provide an implementation that returns all present values of the
         * given propagation {@code key}.
         * @since 1.16.0
         */
        default Iterable<String> getAll(C carrier, String key) {
            String firstValue = get(carrier, key);
            if (firstValue == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(firstValue);
        }

    }

}
