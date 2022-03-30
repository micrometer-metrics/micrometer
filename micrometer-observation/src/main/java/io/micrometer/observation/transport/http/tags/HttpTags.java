/**
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
package io.micrometer.observation.transport.http.tags;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;
import io.micrometer.common.util.StringUtils;

/**
 * Utility class providing convenience methods to generate tags for HTTP metrics based on
 * the {@link HttpRequest} and {@link HttpResponse} abstraction.
 *
 * @author Jon Schneider
 * @since 2.0.0
 */
public class HttpTags {
    private static final String METHOD = "method";
    private static final String STATUS = "status";
    private static final String EXCEPTION = "exception";
    private static final String URI = "uri";

    private static final String UNKNOWN = "UNKNOWN";

    private static final KeyValue EXCEPTION_NONE = KeyValue.of(EXCEPTION, "None");

    private static final KeyValue STATUS_UNKNOWN = KeyValue.of(STATUS, UNKNOWN);

    private static final KeyValue METHOD_UNKNOWN = KeyValue.of(METHOD, UNKNOWN);

    private static final KeyValue URI_UNKNOWN = KeyValue.of(URI, UNKNOWN);

    private HttpTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link HttpRequest#method()
     * method} of the given {@code request}.
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static KeyValue method(HttpRequest request) {
        return (request != null) ? KeyValue.of(METHOD, request.method()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the status tag derived from the status of the response
     */
    public static KeyValue status(HttpResponse response) {
        return (response != null) ? KeyValue.of(STATUS, Integer.toString(response.statusCode())) : STATUS_UNKNOWN;
    }

    /**
     * Creates an {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     * @param exception the exception, may be {@code null}
     * @return the exception tag derived from the exception
     */
    public static KeyValue exception(Throwable exception) {
        if (exception != null) {
            String simpleName = exception.getClass().getSimpleName();
            return KeyValue.of(EXCEPTION, StringUtils.isNotBlank(simpleName) ? simpleName : exception.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the outcome tag derived from the status of the response
     */
    public static KeyValue outcome(HttpResponse response) {
        return ((response != null) ? Outcome.forStatus(response.statusCode()) : Outcome.UNKNOWN).asTag();
    }

    public static KeyValue uri(HttpRequest request) {
        String uri = request.route();
        return uri == null ? URI_UNKNOWN : KeyValue.of(URI, uri);
    }
}
