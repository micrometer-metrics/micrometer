/*
 * Copyright 2023 VMware, Inc.
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

package io.micrometer.jakarta9.instrument.jms;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} interface for
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PUBLISH JMS message publication}
 * operations.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public interface JmsPublishObservationConvention extends ObservationConvention<JmsPublishObservationContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof JmsPublishObservationContext;
    }

}
