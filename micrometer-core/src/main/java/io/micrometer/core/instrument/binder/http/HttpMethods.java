/*
 * Copyright 2025 VMware, Inc.
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

import java.util.HashSet;
import java.util.Set;

/**
 * Utility functions related to HTTP methods.
 *
 * @since 1.16.0
 */
public final class HttpMethods {

    private static final Set<String> STANDARD_HTTP_METHODS = new HashSet<>(9);

    static {
        STANDARD_HTTP_METHODS.add("GET");
        STANDARD_HTTP_METHODS.add("HEAD");
        STANDARD_HTTP_METHODS.add("POST");
        STANDARD_HTTP_METHODS.add("PUT");
        STANDARD_HTTP_METHODS.add("DELETE");
        STANDARD_HTTP_METHODS.add("CONNECT");
        STANDARD_HTTP_METHODS.add("OPTIONS");
        STANDARD_HTTP_METHODS.add("TRACE");
        STANDARD_HTTP_METHODS.add("PATCH");

    }

    private HttpMethods() {
    }

    /**
     * Checks if the given method is a well-known HTTP method defined in
     * <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC 9110</a> and
     * <a href="https://www.rfc-editor.org/rfc/rfc5789.html">RFC 5789</a>. This is
     * case-sensitive and known methods are all uppercase.
     * @param method HTTP method to check
     * @return {@code true} if the given method is a standard HTTP method
     */
    public static boolean isStandard(String method) {
        return STANDARD_HTTP_METHODS.contains(method);
    }

}
