/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.registry.otlp.internal;

import io.opentelemetry.proto.metrics.v1.Exemplar;

import java.util.Collections;
import java.util.List;

/**
 * <strong> This is an internal component and might have breaking changes, external
 * components SHOULD NOT rely on it.</strong>
 */
public interface OtlpExemplarsSupport {

    /**
     * @return the sampled exemplars
     */
    default List<Exemplar> exemplars() {
        return Collections.emptyList();
    }

    /**
     * Rolls the values regardless of the clock or current time and ensures the value will
     * never roll over again after.
     */
    default void closingExemplarsRollover() {
    }

}
