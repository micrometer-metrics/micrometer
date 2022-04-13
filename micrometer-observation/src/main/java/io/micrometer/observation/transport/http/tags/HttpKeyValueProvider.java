/**
 * Copyright 2022 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.observation.transport.http.tags;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;

/**
 * Provides tags for HTTP-related metrics.
 *
 * @see HttpKeyValues
 * @author Tommy Ludwig
 * @since 1.10.0
 */
public interface HttpKeyValueProvider {

    /**
     * Default implementation.
     */
    HttpKeyValueProvider DEFAULT = new DefaultHttpKeyValueProvider();

    /**
     * Provide tags known to be low-cardinality, generally appropriate for use with metrics.
     * These tags should not overlap with the tags provided by {@link #getHighCardinalityKeyValues(HttpRequest, HttpResponse, Throwable)}.
     *
     * @param request http request
     * @param response http response
     * @param exception exception thrown during operation, or null
     * @return set of tags based on the given parameters
     */
    KeyValues getLowCardinalityKeyValues(HttpRequest request, HttpResponse response, Throwable exception);

    /**
     * Provide tags known to be high-cardinality, which generally are not appropriate for use with metrics.
     * These tags should not overlap with the tags provided by {@link #getLowCardinalityKeyValues(HttpRequest, HttpResponse, Throwable)}.
     *
     * @param request http request
     * @param response http response
     * @param exception exception thrown during operation, or null
     * @return set of tags based on the given parameters
     */
    KeyValues getHighCardinalityKeyValues(HttpRequest request, HttpResponse response, Throwable exception);
}
