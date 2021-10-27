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

/**
 * Describes how for a given object a span should be named. In the vast majority of cases
 * a name should be provided explicitly. In case of instrumentation where the name has to
 * be resolved at runtime this interface will provide the name of the span.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface SpanNamer {

	/**
	 * Retrieves the span name for the given object.
	 *
	 * @param object - object for which span name should be picked
	 * @param defaultValue - the default valued to be returned if span name can't be
	 * calculated
	 * @return span name
	 */
	String name(Object object, String defaultValue);

}
