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
package io.micrometer.api.instrument.transport.http.tags;

import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.transport.http.HttpRequest;
import io.micrometer.api.instrument.transport.http.HttpResponse;

/**
 * Provides tags for HTTP-related metrics.
 *
 * @see HttpTags
 * @author Tommy Ludwig
 * @since 2.0.0
 */
public interface HttpTagsProvider {

    /**
     * Default implementation.
     */
    HttpTagsProvider DEFAULT = new DefaultHttpTagsProvider();

    /**
     * Provide tags known to be low-cardinality, generally appropriate for use with metrics.
     * These tags should not overlap with the tags provided by {@link #getHighCardinalityTags(HttpRequest, HttpResponse, Throwable)}.
     *
     * @param request http request
     * @param response http response
     * @param exception exception thrown during operation, or null
     * @return set of tags based on the given parameters
     */
    Tags getLowCardinalityTags(HttpRequest request, HttpResponse response, Throwable exception);

    /**
     * Provide tags known to be high-cardinality, which generally are not appropriate for use with metrics.
     * These tags should not overlap with the tags provided by {@link #getLowCardinalityTags(HttpRequest, HttpResponse, Throwable)}.
     *
     * @param request http request
     * @param response http response
     * @param exception exception thrown during operation, or null
     * @return set of tags based on the given parameters
     */
    Tags getHighCardinalityTags(HttpRequest request, HttpResponse response, Throwable exception);
}
