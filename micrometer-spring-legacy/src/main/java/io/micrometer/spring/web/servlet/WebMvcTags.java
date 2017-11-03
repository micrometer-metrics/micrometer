/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.web.servlet;

import io.micrometer.core.instrument.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Factory methods for {@link Tag Tags} associated with a request-response exchange that
 * is handled by Spring MVC.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 */
public final class WebMvcTags {

    private WebMvcTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link HttpServletRequest#getMethod()
     * method} of the given {@code request}.
     *
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static Tag method(HttpServletRequest request) {
        return Tag.of("method", request.getMethod());
    }

    /**
     * Creates a {@code method} tag based on the status of the given {@code response}.
     *
     * @param response the HTTP response
     * @return the status tag derived from the status of the response
     */
    public static Tag status(HttpServletResponse response) {
        return Tag.of("status", ((Integer) response.getStatus()).toString());
    }

    /**
     * Creates a {@code uri} tag based on the URI of the given {@code request}. Uses the
     * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} best matching pattern if
     * available, falling back to the request's {@link HttpServletRequest#getPathInfo()
     * path info} if necessary.
     *
     * @param request the request
     * @return the uri tag derived from the request
     */
    public static Tag uri(HttpServletRequest request, HttpServletResponse response) {
        if(response != null) {
            HttpStatus status = HttpStatus.valueOf(response.getStatus());
            if (status.is3xxRedirection()) {
                return Tag.of("uri", "REDIRECTION");
            } else if (status.equals(HttpStatus.NOT_FOUND)) {
                return Tag.of("uri", "NOT_FOUND");
            }
        } else {
            // Long task timers won't be initiated if there is no handler found, as they aren't auto-timed.
            // If no handler is found, 30
        }

        String uri = (String) request
            .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (uri == null) {
            uri = request.getPathInfo();
        }
        if (!StringUtils.hasText(uri)) {
            uri = "/";
        }
        return Tag.of("uri", uri.isEmpty() ? "root" : uri);
    }

    /**
     * Creates a {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     *
     * @param exception the exception, may be {@code null}
     * @return the exception tag derived from the exception
     */
    public static Tag exception(Throwable exception) {
        if (exception != null) {
            return Tag.of("exception", exception.getClass().getSimpleName());
        }
        return Tag.of("exception", "None");
    }

}
