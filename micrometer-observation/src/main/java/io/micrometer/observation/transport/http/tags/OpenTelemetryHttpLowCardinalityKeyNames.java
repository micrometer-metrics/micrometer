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

import io.micrometer.common.docs.KeyName;
import io.micrometer.conventions.semantic.SemanticAttributes;

/**
 * Conventions for HTTP key names implemented with OpenTelemetry.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum OpenTelemetryHttpLowCardinalityKeyNames implements KeyName {
    /**
     * HTTP request method.
     *
     * Examples: GET; POST; HEAD
     * @param request
     * @return
     */
    METHOD {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_METHOD.getKey();
        }
    },

    /**
     * Full HTTP request URL in the form scheme://host[:port]/path?query[#fragment].
     * Usually the fragment is not transmitted over HTTP, but if it is known, it should be
     * included nevertheless.
     *
     * Examples: https://www.foo.bar/search?q=OpenTelemetry#SemConv
     * @param request
     * @return
     */
    URL {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_URL.getKey();
        }

    },

    /**
     * The full request target as passed in a HTTP request line or equivalent.
     *
     * Examples: /path/12314/?q=ddds#123
     * @param request
     * @return
     */
    TARGET {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_TARGET.getKey();
        }

    },

    /**
     * The value of the HTTP host header. An empty Host header should also be reported,
     * see note.
     *
     * Examples: www.example.org
     * @param request
     * @return
     */
    HOST {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_HOST.getKey();
        }

    },

    /**
     * The URI scheme identifying the used protocol.
     *
     * Examples: http; https
     * @param request
     * @return
     */
    SCHEME {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_SCHEME.getKey();
        }

    },

    /**
     * HTTP response status code.
     *
     * Examples: 200
     * @param response
     * @return
     */
    STATUS_CODE {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_STATUS_CODE.getKey();
        }

    },

    /**
     * Kind of HTTP protocol used.
     *
     * Examples: 1.0
     * @param request
     * @return
     */
    FLAVOR {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_FLAVOR.getKey();
        }

    },

    /**
     * Value of the HTTP User-Agent header sent by the client.
     *
     * Examples: CERN-LineMode/2.15 libwww/2.17b3
     * @param request
     * @return
     */
    USER_AGENT {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_USER_AGENT.getKey();
        }

    },

    /**
     * The size of the request payload body in bytes. This is the number of bytes
     * transferred excluding headers and is often, but not always, present as the
     * Content-Length header. For requests using transport encoding, this should be the
     * compressed size.
     *
     * Examples: 3495
     * @param request
     * @return
     */
    REQUEST_CONTENT_LENGTH {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH.getKey();
        }

    },

    /**
     * The size of the response payload body in bytes. This is the number of bytes
     * transferred excluding headers and is often, but not always, present as the
     * Content-Length header. For requests using transport encoding, this should be the
     * compressed size.
     *
     * Examples: 3495
     * @param response
     * @return
     */
    RESPONSE_CONTENT_LENGTH {
        @Override
        public String getKeyName() {
            return SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.getKey();
        }

    },

    /**
     * Remote address of the peer (dotted decimal for IPv4 or RFC5952 for IPv6)
     *
     * Examples: 127.0.0.1
     * @param request
     * @return
     */
    IP {
        @Override
        public String getKeyName() {
            return SemanticAttributes.NET_PEER_IP.getKey();
        }

    },

    /**
     * Remote port number.
     *
     * Examples: 80; 8080; 443
     * @param request
     * @return
     */
    PORT {
        @Override
        public String getKeyName() {
            return SemanticAttributes.NET_PEER_PORT.getKey();
        }

    }
}
