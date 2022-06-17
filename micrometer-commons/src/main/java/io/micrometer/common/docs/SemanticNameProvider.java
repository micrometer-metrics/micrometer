/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.common.docs;

/**
 * Renames the metric / trace / observation depending on standards.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface SemanticNameProvider<T> {

    /**
     * Will return a standardized name.
     * @return name
     */
    String getName();

    /**
     * Returns {@code true} when this {@link SemanticNameProvider} should be applied and a
     * new name should be set.
     * @param object object against which we determine whether this provider is applicable
     * or not
     * @return {@code true} when new name should be applied
     */
    boolean isApplicable(T object);

}
