/*
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.core.ipc.http;

/**
 * The class of HTTP status. Simplification of
 * {@code io.netty.handler.codec.http.HttpStatusClass}.
 *
 * @author Jon Schneider
 */
enum HttpStatusClass {

    // @formatter:off
    INFORMATIONAL(100, 200),
    SUCCESS(200, 300),
    REDIRECTION(300, 400),
    CLIENT_ERROR(400, 500),
    SERVER_ERROR(500, 600),
    // @formatter:on
    UNKNOWN(0, 0) {
        @Override
        public boolean contains(int code) {
            return code < 100 || code >= 600;
        }
    };

    /**
     * Returns the class of the specified HTTP status code.
     */
    public static HttpStatusClass valueOf(int code) {
        if (INFORMATIONAL.contains(code)) {
            return INFORMATIONAL;
        }
        if (SUCCESS.contains(code)) {
            return SUCCESS;
        }
        if (REDIRECTION.contains(code)) {
            return REDIRECTION;
        }
        if (CLIENT_ERROR.contains(code)) {
            return CLIENT_ERROR;
        }
        if (SERVER_ERROR.contains(code)) {
            return SERVER_ERROR;
        }
        return UNKNOWN;
    }

    private final int min;

    private final int max;

    HttpStatusClass(int min, int max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Returns {@code true} if and only if the specified HTTP status code falls into this
     * class.
     */
    public boolean contains(int code) {
        return code >= min && code < max;
    }

}
