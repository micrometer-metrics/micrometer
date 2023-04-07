/**
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
package io.micrometer.common.annotation;

/**
 * Resolves the tag value for the given parameter and the provided expression.
 *
 * @author Marcin Grzejszczak
 * @since 1.11.0
 */
public interface TagValueExpressionResolver {

    /**
     * Returns the tag value for the given parameter and the provided expression.
     * @param expression the expression coming from a tag
     * @param parameter parameter annotated with a tag related annotation
     * @return the value of the tag
     */
    String resolve(String expression, Object parameter);

}
