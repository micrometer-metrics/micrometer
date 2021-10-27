/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micrometer.core.instrument.transport;

/**
 * Represents side of communication.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public enum Kind {

	/**
	 * Indicates that the span covers server-side handling of an RPC or other remote
	 * request.
	 */
	SERVER,

	/**
	 * Indicates that the span covers the client-side wrapper around an RPC or other
	 * remote request.
	 */
	CLIENT,

	/**
	 * Indicates that the span describes producer sending a message to a broker. Unlike
	 * client and server, there is no direct critical path latency relationship between
	 * producer and consumer spans.
	 */
	PRODUCER,

	/**
	 * Indicates that the span describes consumer receiving a message from a broker.
	 * Unlike client and server, there is no direct critical path latency relationship
	 * between producer and consumer spans.
	 */
	CONSUMER

}
