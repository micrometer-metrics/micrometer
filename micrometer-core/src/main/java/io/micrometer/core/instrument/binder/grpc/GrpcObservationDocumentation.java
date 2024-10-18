/*
 * Copyright 2022 the original author or authors.
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
package io.micrometer.core.instrument.binder.grpc;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.Observation.Event;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * {@link ObservationDocumentation} for gRPC.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.10.0
 */
public enum GrpcObservationDocumentation implements ObservationDocumentation {

    CLIENT {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return GrpcClientObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }
    },
    SERVER {
        @Override
        public Class<? extends ObservationConvention<? extends Context>> getDefaultConvention() {
            return GrpcServerObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return LowCardinalityKeyNames.values();
        }
    };

    public enum LowCardinalityKeyNames implements KeyName {

        METHOD {
            @Override
            public String asString() {
                return "rpc.method";
            }
        },
        METHOD_TYPE {
            @Override
            public String asString() {
                return "rpc.type";
            }
        },
        SERVICE {
            @Override
            public String asString() {
                return "rpc.service";
            }
        },
        ERROR_CODE {
            @Override
            public String asString() {
                return "rpc.error_code";
            }
        },
        STATUS_CODE {
            @Override
            public String asString() {
                return "grpc.status_code";
            }
        },
        PEER_NAME {
            @Override
            public String asString() {
                return "net.peer.name";
            }
        },
        PEER_PORT {
            @Override
            public String asString() {
                return "net.peer.port";
            }

        }

    }

    public enum GrpcClientEvents implements Event {

        MESSAGE_SENT {
            @Override
            public String getName() {
                return "sent";
            }

        },
        MESSAGE_RECEIVED {
            @Override
            public String getName() {
                return "received";
            }

        }

    }

    public enum GrpcServerEvents implements Event {

        MESSAGE_RECEIVED {
            @Override
            public String getName() {
                return "received";
            }

        },
        MESSAGE_SENT {
            @Override
            public String getName() {
                return "sent";
            }

        },
        /**
         * For a canceled event.
         * @since 1.14.0
         */
        CANCELLED {
            @Override
            public String getName() {
                return "cancelled";
            }
        }

    }

}
