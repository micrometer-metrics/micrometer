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
package io.micrometer.observation;

/**
 * Allows to modify the {@link Observation.Context} on stopping the {@link Observation}
 * before the {@link ObservationHandler} implementations process it.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public interface ObservationFilter {

    /**
     * Mutates the {@link Observation.Context}.
     * @param context context to modify
     * @return modified context
     */
    Observation.Context map(Observation.Context context);

}
