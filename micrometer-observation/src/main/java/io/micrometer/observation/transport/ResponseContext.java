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
package io.micrometer.observation.transport;

import io.micrometer.common.lang.Nullable;

/**
 * Common interface for getting/setting the response object on
 * {@link io.micrometer.observation.Observation.Context} implementations that handle a
 * response.
 *
 * @param <RES> type of response object
 * @since 1.10.0
 */
public interface ResponseContext<RES> {

    /**
     * Getter for the response object.
     * @return the response
     */
    @Nullable
    RES getResponse();

    /**
     * Setter for the response object.
     * @param response the response
     */
    void setResponse(RES response);

}
