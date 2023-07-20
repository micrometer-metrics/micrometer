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

import io.micrometer.common.KeyValue;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * Documentation of HTTP based observations.
 *
 * @author Brian Clozel
 * @author Marcin Grzejszczak
 * @since 1.12.0
 */
public enum HttpObservationDocumentation implements ObservationDocumentation {

    /**
     * Observation created when a request is sent out via Jakarta API.
     */
    JAKARTA_CLIENT_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpJakartaClientRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(CommonLowCardinalityKeys.values(), ClientLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return KeyName.merge(CommonHighCardinalityKeys.values(), ClientHighCardinalityKeys.values());
        }
    },

    /**
     * Observation created when a request is received with Jakarta Http.
     */
    JAKARTA_SERVER_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpJakartaServerRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {

            return KeyName.merge(CommonLowCardinalityKeys.values(), ServerLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return CommonHighCardinalityKeys.values();
        }

    },

    /**
     * Observation created when a request is received with Javax Http.
     */
    SERVER_OBSERVATION {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return DefaultHttpServerRequestObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return KeyName.merge(CommonLowCardinalityKeys.values(), ServerLowCardinalityKeys.values());
        }

        @Override
        public KeyName[] getHighCardinalityKeyNames() {
            return CommonHighCardinalityKeys.values();
        }

    };

    enum CommonLowCardinalityKeys implements KeyName {

        /**
         * HTTP request method.
         */
        HTTP_REQUEST_METHOD {
            @Override
            public String asString() {
                return "http.request.method";
            }
        },

        /**
         * HTTP response status code.
         */
        HTTP_RESPONSE_STATUS_CODE {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public String asString() {
                return "http.response.status_code";
            }
        },

        /**
         * OSI Application Layer or non-OSI equivalent. The value SHOULD be normalized to
         * lowercase.
         */
        NETWORK_PROTOCOL_NAME {
            @Override
            public String asString() {
                return "network.protocol.name";
            }
        },

        /**
         * Name of the local HTTP server that received the request.
         */
        SERVER_ADDRESS {
            @Override
            public String asString() {
                return "server.address";
            }

        },

        /**
         * Port of the local HTTP server that received the request.
         */
        SERVER_PORT {
            @Override
            public String asString() {
                return "server.port";
            }

        },

        /**
         * The URI scheme component identifying the used protocol.
         */
        URL_SCHEME {
            @Override
            public String asString() {
                return "url.scheme";
            }
        },

        /**
         * Name of HTTP request method or {@value KeyValue#NONE_VALUE} if the request was
         * not received properly.
         */
        METHOD {
            @Override
            public String asString() {
                return "method";
            }

        },

        /**
         * HTTP response raw status code, or {@code "UNKNOWN"} if no response was created.
         */
        STATUS {
            @Override
            public String asString() {
                return "status";
            }
        },

        /**
         * URI pattern for the matching handler if available, falling back to
         * {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND} for 404 responses,
         * {@code root} for requests with no path info, and {@code UNKNOWN} for all other
         * requests.
         */
        URI {
            @Override
            public String asString() {
                return "uri";
            }
        },

        /**
         * Name of the exception thrown during the exchange, or
         * {@value KeyValue#NONE_VALUE}} if no exception happened.
         */
        EXCEPTION {
            @Override
            public String asString() {
                return "exception";
            }
        },

        /**
         * Outcome of the HTTP server exchange.
         */
        OUTCOME {
            @Override
            public String asString() {
                return "outcome";
            }
        }

    }

    enum ServerLowCardinalityKeys implements KeyName {

        /**
         * The matched route (path template in the format used by the respective server
         * framework).
         */
        HTTP_ROUTE {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public String asString() {
                return "http.route";
            }
        }

    }

    enum ClientLowCardinalityKeys implements KeyName {

        /**
         * Client name derived from the request URI host.
         */
        CLIENT_NAME {
            @Override
            public String asString() {
                return "client.name";
            }
        }

    }

    enum CommonHighCardinalityKeys implements KeyName {

        /**
         * The size of the request payload body in bytes. This is the number of bytes
         * transferred excluding headers and is often, but not always, present as the
         * Content-Length header. For requests using transport encoding, this should be
         * the compressed size.
         */
        REQUEST_BODY_SIZE {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public String asString() {
                return "http.request.body.size";
            }
        },

        /**
         * The size of the response payload body in bytes. This is the number of bytes
         * transferred excluding headers and is often, but not always, present as the
         * Content-Length header. For requests using transport encoding, this should be
         * the compressed size.
         */
        RESPONSE_BODY_SIZE {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public String asString() {
                return "http.response.body.size";
            }

        },

        /**
         * Value of the HTTP User-Agent header sent by the client.
         */
        USER_AGENT_ORIGINAL {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public String asString() {
                return "user_agent.original";
            }

        },

        /**
         * HTTP request URI.
         */
        HTTP_URL {
            @Override
            public String asString() {
                return "http.url";
            }
        }

    }

    enum ClientHighCardinalityKeys implements KeyName {

        /**
         * Absolute URL describing a network resource according to RFC3986
         */
        URL_FULL {
            @Override
            public String asString() {
                return "url.full";
            }
        }

    }

}
