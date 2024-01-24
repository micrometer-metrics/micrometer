/**
 * Copyright 2023 the original author or authors.
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

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;

/**
 * Resolves the {@link KeyValue} value for the given parameter and the provided
 * expression.
 *
 * @author Marcin Grzejszczak
 * @since 1.11.0
 */
public interface ValueExpressionResolver {

    /**
     * Returns the {@link KeyValue} value for the given parameter and the provided
     * expression.
     * @param expression the expression coming from an annotation
     * @param parameter parameter annotated with a {@link KeyValue} related annotation
     * @return the value of the {@link KeyValue}
     */
    @Nullable
    String resolve(String expression, @Nullable Object parameter);

}
