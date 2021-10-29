/*
 * Copyright 2021-2021 the original author or authors.
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

package io.micrometer.core.instrument;

/**
 * Indicates whether the {@link Tag} is a high cardinality tag or low cardinality tag.
 *
 * The cardinality of a set (possible valid values of a tag) is a measure of the "number
 * of elements" of that set. For example a user Id is a high-cardinality tag since the
 * number of elements of the valid set of user Ids can be high. The eye color though has
 * low cardinality since the number of possible values of that set is low. see:
 * https://en.wikipedia.org/wiki/Cardinality
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 */
public enum Cardinality {

	/**
	 * Represents low cardinality data.
	 */
	LOW,

	/**
	 * Represents high cardinality data.
	 */
	HIGH

}
