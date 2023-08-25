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

import io.micrometer.common.util.StringUtils;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Tags for HTTP requests.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
public class HttpRequestTags {

    private static final Tag EXCEPTION_NONE = Tag.of("exception", "None");

    private static final Tag STATUS_UNKNOWN = Tag.of("status", "UNKNOWN");

    private static final Tag METHOD_UNKNOWN = Tag.of("method", "UNKNOWN");

    private HttpRequestTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link HttpServletRequest#getMethod()
     * method} of the given {@code request}.
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static Tag method(HttpServletRequest request) {
        return (request != null) ? Tag.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code method} tag based on the
     * {@link jakarta.servlet.http.HttpServletRequest#getMethod() method} of the given
     * {@code request}.
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     * @since 1.10.0
     * @deprecated since 1.12.0 in favor of
     * {@link HttpJakartaServletRequestTags#method(jakarta.servlet.http.HttpServletRequest)}
     */
    @Deprecated
    public static Tag method(jakarta.servlet.http.HttpServletRequest request) {
        return HttpJakartaServletRequestTags.method(request);
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the status tag derived from the status of the response
     */
    public static Tag status(HttpServletResponse response) {
        return (response != null) ? Tag.of("status", Integer.toString(response.getStatus())) : STATUS_UNKNOWN;
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the status tag derived from the status of the response
     * @since 1.10.0
     * @deprecated since 1.12.0 in favor of
     * {@link HttpJakartaServletRequestTags#status(jakarta.servlet.http.HttpServletResponse)}
     */
    @Deprecated
    public static Tag status(jakarta.servlet.http.HttpServletResponse response) {
        return HttpJakartaServletRequestTags.status(response);
    }

    /**
     * Creates an {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     * @param exception the exception, may be {@code null}
     * @return the exception tag derived from the exception
     */
    public static Tag exception(Throwable exception) {
        if (exception != null) {
            String simpleName = exception.getClass().getSimpleName();
            return Tag.of("exception",
                    StringUtils.isNotBlank(simpleName) ? simpleName : exception.getClass().getName());
        }
        return EXCEPTION_NONE;
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the outcome tag derived from the status of the response
     */
    public static Tag outcome(HttpServletResponse response) {
        Outcome outcome = (response != null) ? Outcome.forStatus(response.getStatus()) : Outcome.UNKNOWN;
        return outcome.asTag();
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code response}.
     * @param response the HTTP response
     * @return the outcome tag derived from the status of the response
     * @since 1.10.0
     * @deprecated since 1.12.0 in favor of
     * {@link HttpJakartaServletRequestTags#outcome(jakarta.servlet.http.HttpServletResponse)}
     */
    @Deprecated
    public static Tag outcome(jakarta.servlet.http.HttpServletResponse response) {
        return HttpJakartaServletRequestTags.outcome(response);
    }

}
