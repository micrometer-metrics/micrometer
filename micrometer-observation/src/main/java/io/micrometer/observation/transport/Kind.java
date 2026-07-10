/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.observation.transport;

/**
 * Represents side of communication.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public enum Kind {

    /**
     * Indicates that the operation covers server-side handling of an RPC or other remote
     * request.
     */
    SERVER,

    /**
     * Indicates that the operation covers the client-side wrapper around an RPC or other
     * remote request.
     */
    CLIENT,

    /**
     * Indicates that the operation describes producer sending a message to a broker.
     * Unlike client and server, there is no direct critical path latency relationship
     * between producer and consumer operations.
     */
    PRODUCER,

    /**
     * Indicates that the operation describes consumer receiving a message from a broker.
     * Unlike client and server, there is no direct critical path latency relationship
     * between producer and consumer operation.
     */
    CONSUMER

}
