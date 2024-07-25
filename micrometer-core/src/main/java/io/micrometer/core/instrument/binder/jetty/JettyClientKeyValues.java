/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.common.util.StringUtils;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpStatus;

import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Factory methods for {@link KeyValue} associated with a request-response exchange that
 * is handled by Jetty {@link org.eclipse.jetty.client.HttpClient}.
 *
 * @author Jon Schneider
 * @since 1.11.0
 * @deprecated since 1.13.0 in favor of the micrometer-jetty12 module
 */
@Deprecated
public final class JettyClientKeyValues {

    private static final KeyValue URI_NOT_FOUND = KeyValue.of("uri", "NOT_FOUND");

    private static final KeyValue URI_REDIRECTION = KeyValue.of("uri", "REDIRECTION");

    private static final KeyValue URI_ROOT = KeyValue.of("uri", "root");

    private static final KeyValue EXCEPTION_NONE = KeyValue.of("exception", "None");

    private static final KeyValue EXCEPTION_UNKNOWN = KeyValue.of("exception", "UNKNOWN");

    private static final KeyValue METHOD_UNKNOWN = KeyValue.of("method", "UNKNOWN");

    private static final KeyValue HOST_UNKNOWN = KeyValue.of("host", "UNKNOWN");

    private static final KeyValue STATUS_UNKNOWN = KeyValue.of("status", "UNKNOWN");

    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/$");

    private static final Pattern MULTIPLE_SLASH_PATTERN = Pattern.compile("//+");

    private static final KeyValue OUTCOME_UNKNOWN = KeyValue.of("outcome", "UNKNOWN");

    private JettyClientKeyValues() {
    }

    /**
     * Creates a {@code method} KeyValue based on the {@link Request#getMethod() method}
     * of the given {@code request}.
     * @param request the request
     * @return the method KeyValue whose value is a capitalized method (e.g. GET).
     */
    public static KeyValue method(Request request) {
        return (request != null) ? KeyValue.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    /**
     * Creates a {@code host} KeyValue based on the {@link Request#getHost()} of the given
     * {@code request}.
     * @param request the request
     * @return the host KeyValue derived from request
     */
    public static KeyValue host(Request request) {
        return (request != null) ? KeyValue.of("host", request.getHost()) : HOST_UNKNOWN;
    }

    /**
     * Creates a {@code status} KeyValue based on the status of the given {@code result}.
     * @param result the request result
     * @return the status KeyValue derived from the status of the response
     */
    public static KeyValue status(@Nullable Result result) {
        return result != null ? KeyValue.of("status", Integer.toString(result.getResponse().getStatus()))
                : STATUS_UNKNOWN;
    }

    /**
     * Creates a {@code uri} KeyValue based on the URI of the given {@code result}.
     * {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND} for 404 responses.
     * @param request the request
     * @param result the request result
     * @param successfulUriPattern successful URI pattern
     * @return the uri KeyValue derived from the request and its result
     */
    public static KeyValue uri(Request request, @Nullable Result result,
            BiFunction<Request, Result, String> successfulUriPattern) {
        if (result != null && result.getResponse() != null) {
            int status = result.getResponse().getStatus();
            if (HttpStatus.isRedirection(status)) {
                return URI_REDIRECTION;
            }
            if (status == 404) {
                return URI_NOT_FOUND;
            }
        }

        String matchingPattern = successfulUriPattern.apply(request, result);
        matchingPattern = MULTIPLE_SLASH_PATTERN.matcher(matchingPattern).replaceAll("/");
        if (matchingPattern.equals("/")) {
            return URI_ROOT;
        }
        matchingPattern = TRAILING_SLASH_PATTERN.matcher(matchingPattern).replaceAll("");
        return KeyValue.of("uri", matchingPattern);
    }

    /**
     * Creates an {@code exception} KeyValue based on the {@link Class#getSimpleName()
     * simple name} of the class of the given {@code exception}.
     * @param result the request result
     * @return the exception KeyValue derived from the exception
     */
    public static KeyValue exception(@Nullable Result result) {
        if (result == null) {
            return EXCEPTION_UNKNOWN;
        }
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
        return KeyValue.of("exception",
                StringUtils.isNotEmpty(simpleName) ? simpleName : exception.getClass().getName());
    }

    /**
     * Creates an {@code outcome} KeyValue based on the status of the given
     * {@code result}.
     * @param result the request result
     * @return the outcome KeyValue derived from the status of the response
     */
    public static KeyValue outcome(@Nullable Result result) {
        if (result == null) {
            return OUTCOME_UNKNOWN;
        }
        return Outcome.forStatus(result.getResponse().getStatus()).asKeyValue();
    }

}
