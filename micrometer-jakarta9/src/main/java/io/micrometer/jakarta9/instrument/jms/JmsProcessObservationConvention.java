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

<<<<<<<< HEAD:micrometer-jakarta9/src/main/java/io/micrometer/jakarta9/instrument/jms/JmsProcessObservationConvention.java
package io.micrometer.jakarta9.instrument.jms;
========
package io.micrometer.jakarta.instrument.binder.jms;
>>>>>>>> aaa5c09f2 (Moved Jakarta and latest Jakarta based dependencies):micrometer-jakarta/src/main/java/io/micrometer/jakarta/instrument/binder/jms/JmsProcessObservationConvention.java

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * {@link ObservationConvention} interface for
 * {@link JmsObservationDocumentation#JMS_MESSAGE_PUBLISH JMS message process} operations.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public interface JmsProcessObservationConvention extends ObservationConvention<JmsProcessObservationContext> {

    @Override
    default boolean supportsContext(Observation.Context context) {
        return context instanceof JmsProcessObservationContext;
    }

}
