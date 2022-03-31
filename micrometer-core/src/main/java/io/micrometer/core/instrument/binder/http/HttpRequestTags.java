/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.util.StringUtils;

/**
 * Tags for HTTP requests.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
public class HttpRequestTags {
    private static final io.micrometer.common.Tag EXCEPTION_NONE = io.micrometer.common.Tag.of("exception", "None");

    private static final io.micrometer.common.Tag STATUS_UNKNOWN = io.micrometer.common.Tag.of("status", "UNKNOWN");

    private static final io.micrometer.common.Tag METHOD_UNKNOWN = io.micrometer.common.Tag.of("method", "UNKNOWN");

    private HttpRequestTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link HttpServletRequest#getMethod()
     * method} of the given {@code request}.
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static io.micrometer.common.Tag method(HttpServletRequest request) {
        return (request != null) ? io.micrometer.common.Tag.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the status tag derived from the status of the response
     */
    public static io.micrometer.common.Tag status(HttpServletResponse response) {
        return (response != null) ? io.micrometer.common.Tag.of("status", Integer.toString(response.getStatus())) : STATUS_UNKNOWN;
    }

    /**
     * Creates a {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     * @param exception the exception, may be {@code null}
     * @return the exception tag derived from the exception
     */
    public static io.micrometer.common.Tag exception(Throwable exception) {
        if (exception != null) {
            String simpleName = exception.getClass().getSimpleName();
            return io.micrometer.common.Tag.of("exception", StringUtils.isNotBlank(simpleName) ? simpleName : exception.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the outcome tag derived from the status of the response
     */
    public static io.micrometer.common.Tag outcome(HttpServletResponse response) {
        Outcome outcome = (response != null) ? Outcome.forStatus(response.getStatus()) : Outcome.UNKNOWN;
        return outcome.asTag();
    }
}
