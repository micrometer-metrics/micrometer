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
package io.micrometer.binder.jetty;

import java.util.function.Function;
import java.util.regex.Pattern;

import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.util.StringUtils;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpStatus;

/**
 * Factory methods for {@link io.micrometer.common.Tag Tags} associated with a request-response exchange that
 * is handled by Jetty {@link org.eclipse.jetty.client.HttpClient}.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
public final class JettyClientTags {

    private static final io.micrometer.common.Tag URI_NOT_FOUND = io.micrometer.common.Tag.of("uri", "NOT_FOUND");

    private static final io.micrometer.common.Tag URI_REDIRECTION = io.micrometer.common.Tag.of("uri", "REDIRECTION");

    private static final io.micrometer.common.Tag URI_ROOT = io.micrometer.common.Tag.of("uri", "root");

    private static final io.micrometer.common.Tag EXCEPTION_NONE = io.micrometer.common.Tag.of("exception", "None");

    private static final io.micrometer.common.Tag METHOD_UNKNOWN = io.micrometer.common.Tag.of("method", "UNKNOWN");

    private static final io.micrometer.common.Tag HOST_UNKNOWN = io.micrometer.common.Tag.of("host", "UNKNOWN");

    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/$");

    private static final Pattern MULTIPLE_SLASH_PATTERN = Pattern.compile("//+");

    private JettyClientTags() {
    }

    /**
     * Creates a {@code method} tag based on the {@link Request#getMethod()
     * method} of the given {@code request}.
     *
     * @param request the request
     * @return the method tag whose value is a capitalized method (e.g. GET).
     */
    public static io.micrometer.common.Tag method(Request request) {
        return (request != null) ? io.micrometer.common.Tag.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code host} tag based on the {@link Request#getHost()} of the given {@code request}.
     *
     * @param request the request
     * @return the host tag derived from request
     * @since 1.7.0
     */
    public static io.micrometer.common.Tag host(Request request) {
        return (request != null) ? io.micrometer.common.Tag.of("host", request.getHost()) : HOST_UNKNOWN;
    }

    /**
     * Creates a {@code status} tag based on the status of the given {@code result}.
     *
     * @param result the request result
     * @return the status tag derived from the status of the response
     */
    public static io.micrometer.common.Tag status(Result result) {
        return io.micrometer.common.Tag.of("status", Integer.toString(result.getResponse().getStatus()));
    }

    /**
     * Creates a {@code uri} tag based on the URI of the given {@code result}.
     * {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND} for 404 responses.
     *
     * @param result the request result
     * @param successfulUriPattern successful URI pattern
     * @return the uri tag derived from the request result
     */
    public static io.micrometer.common.Tag uri(Result result, Function<Result, String> successfulUriPattern) {
        Response response = result.getResponse();
        if (response != null) {
            int status = response.getStatus();
            if (HttpStatus.isRedirection(status)) {
                return URI_REDIRECTION;
            }
            if (status == 404) {
                return URI_NOT_FOUND;
            }
        }

        String matchingPattern = successfulUriPattern.apply(result);
        matchingPattern = MULTIPLE_SLASH_PATTERN.matcher(matchingPattern).replaceAll("/");
        if (matchingPattern.equals("/")) {
            return URI_ROOT;
        }
        matchingPattern = TRAILING_SLASH_PATTERN.matcher(matchingPattern).replaceAll("");
        return io.micrometer.common.Tag.of("uri", matchingPattern);
    }

    /**
     * Creates an {@code exception} tag based on the {@link Class#getSimpleName() simple
     * name} of the class of the given {@code exception}.
     *
     * @param result the request result
     * @return the exception tag derived from the exception
     */
    public static io.micrometer.common.Tag exception(Result result) {
        Throwable exception = result.getFailure();
        if (exception == null) {
            return EXCEPTION_NONE;
        }
        if (result.getResponse() != null) {
            int status = result.getResponse().getStatus();
            if (status == 404 || HttpStatus.isRedirection(status)) {
                return EXCEPTION_NONE;
            }
        }
        if (exception.getCause() != null) {
            exception = exception.getCause();
        }
        String simpleName = exception.getClass().getSimpleName();
        return io.micrometer.common.Tag.of("exception", StringUtils.isNotEmpty(simpleName) ? simpleName
                : exception.getClass().getName());
    }

    /**
     * Creates an {@code outcome} tag based on the status of the given {@code result}.
     *
     * @param result the request result
     * @return the outcome tag derived from the status of the response
     */
    public static io.micrometer.common.Tag outcome(Result result) {
        return Outcome.forStatus(result.getResponse().getStatus()).asTag();
    }

}
