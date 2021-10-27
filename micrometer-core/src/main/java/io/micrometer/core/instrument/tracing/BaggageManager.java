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

package io.micrometer.core.instrument.tracing;

import java.util.Map;

/**
 * Manages {@link BaggageInScope} entries. Upon retrieval / creation of a baggage entry
 * puts it in scope. Scope must be closed.
 *
 * @author OpenTelemetry Authors
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface BaggageManager {

	/**
	 * Returns the mapping of all baggage entries from the given scope.
	 *
	 * @return mapping of all baggage entries
	 */
	Map<String, String> getAllBaggage();

	/**
	 * Retrieves {@link BaggageInScope} for the given name.
	 *
	 * @param name baggage name
	 * @return baggage or {@code null} if not present
	 */
	BaggageInScope getBaggage(String name);

	/**
	 * Retrieves {@link BaggageInScope} for the given name.
	 *
	 * @param traceContext trace context with baggage attached to it
	 * @param name baggage name
	 * @return baggage or {@code null} if not present
	 */
	BaggageInScope getBaggage(TraceContext traceContext, String name);

	/**
	 * Creates a new {@link BaggageInScope} entry for the given name or returns an
	 * existing one if it's already present.
	 *
	 * @param name baggage name
	 * @return new or already created baggage
	 */
	BaggageInScope createBaggage(String name);

	/**
	 * Creates a new {@link BaggageInScope} entry for the given name or returns an
	 * existing one if it's already present.
	 *
	 * @param name baggage name
	 * @param value baggage value
	 * @return new or already created baggage
	 */
	BaggageInScope createBaggage(String name, String value);

}
