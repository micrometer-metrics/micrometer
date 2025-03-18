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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * OpenTelemetry {@link ObservationDocumentation} for Apache HTTP Client 5 instrumentation.
 * <p>
 * Warning: Aligned with <b>STABLE</b> semantic conventions version 1.31.0
 *
 * @since 1.16.0
 * @see ObservationExecChainHandler
 */
public enum OpenTelemetryApacheHttpClientObservationDocumentation implements ObservationDocumentation {

    DEFAULT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultApacheHttpClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return ApacheHttpClientKeyNames.values();
        }
    };

    enum ApacheHttpClientKeyNames implements KeyName {

        /**
         * HTTP request method.
         * <p>
         * Examples: GET; POST; HEAD
         * <p>
         * Requirement level: Required
         */
        HTTP_REQUEST_METHOD {
            @Override
            public String asString() {
                return "http.request.method";
            }
        },

        /**
         * Host identifier of the “URI origin” HTTP request is sent to.
         * <p>
         * Examples: example.com; 10.1.2.80; /tmp/my.sock
         * <p>
         * Requirement level: Required
         */
        SERVER_ADDRESS {
            @Override
            public String asString() {
                return "server.address";
            }
        },

        /**
         * Port identifier of the “URI origin” HTTP request is sent to.
         * <p>
         * Examples: 80; 8080; 443
         * <p>
         * Requirement level: Required
         */
        SERVER_PORT {
            @Override
            public String asString() {
                return "server.address";
            }
        },

        /**
         * Absolute URL describing a network resource according to RFC3986.
         * <p>
         * Examples: https://www.foo.bar/search?q=OpenTelemetry#SemConv; //localhost
         * <p>
         * Requirement level: Required
         */
        URL_FULL {
            @Override
            public String asString() {
                return "url.full";
            }
        },

        /**
         * Describes a class of error the operation ended with.
         * <p>
         * Examples: timeout; java.net.UnknownHostException; server_certificate_invalid; 500
         * <p>
         * Requirement level: Conditionally Required If request has ended with an error.
         */
        ERROR_TYPE {
            @Override
            public String asString() {
                return "error.type";
            }
        },

        /**
         * Original HTTP method sent by the client in the request line.
         * <p>
         * Examples: GeT; ACL; foo
         * <p>
         * Requirement level: Conditionally Required
         */
        HTTP_REQUEST_METHOD_ORIGINAL {
            @Override
            public String asString() {
                return "http.request.method_original";
            }
        },

        /**
         * HTTP response status code.
         * <p>
         * Examples: 200
         * <p>
         * Requirement level: Conditionally Required If and only if one was received/sent.
         */
        HTTP_RESPONSE_STATUS_CODE {
            @Override
            public String asString() {
                return "http.response.status_code";
            }
        },

        /**
         * OSI application layer or non-OSI equivalent.
         * <p>
         * Examples: http; spdy
         * <p>
         * Requirement level: Conditionally Required.
         */
        NETWORK_PROTOCOL_NAME {
            @Override
            public String asString() {
                return "network.protocol.name";
            }
        },

        /**
         * The ordinal number of request resending attempt (for any reason, including redirects).
         * <p>
         * Examples: 3
         * <p>
         * Requirement level: Recommended if and only if request was retried.
         */
        HTTP_REQUEST_RESEND_COUNT {
            @Override
            public String asString() {
                return "http.request.resend_count";
            }
        },

        /**
         * Peer port number of the network connection.
         * <p>
         * Examples: 65123
         * <p>
         * Requirement level: Recommended If network.peer.address is set.
         */
        NETWORK_PEER_ADDRESS {
            @Override
            public String asString() {
                return "network.peer.port";
            }
        },

        /**
         * The actual version of the protocol used for network communication.
         * <p>
         * Examples: 1.0; 1.1; 2; 3
         * <p>
         * Requirement level: Recommended.
         */
        NETWORK_PROTOCOL_VERSION {
            @Override
            public String asString() {
                return "network.protocol.version";
            }
        },

        /**
         * HTTP request headers, {@code <key>} being the normalized HTTP Header name (lowercase), the value being the header values.
         * <p>
         * Examples: http.request.header.content-type=["application/json"]; http.request.header.x-forwarded-for=["1.2.3.4", "1.2.3.5"]
         * <p>
         * Requirement level: Opt-In.
         */
        HTTP_REQUEST_HEADER {
            @Override
            public String asString() {
                return "http.request.header.%s";
            }
        },

        /**
         * HTTP response headers, {@code <key>} being the normalized HTTP Header name (lowercase), the value being the header values.
         * <p>
         * Examples: http.response.header.content-type=["application/json"]; http.response.header.my-custom-header=["abc", "def"]
         * <p>
         * Requirement level: Opt-In.
         */
        HTTP_RESPONSE_HEADER {
            @Override
            public String asString() {
                return "http.response.header.%s";
            }
        },

        /**
         * OSI transport layer or inter-process communication method.
         * <p>
         * Examples: tcp; udp
         * <p>
         * Requirement level: Opt-In.
         */
        NETWORK_TRANSPORT {
            @Override
            public String asString() {
                return "network.transport";
            }
        },

    }

}
