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
package io.micrometer.appdynamics.reporter;

import io.micrometer.core.instrument.Meter;

/**
 * AppDynamics reporter interface.<br>
 * Implementations may target Java Agent API or Machine Agent API.
 *
 * @author Ricardo Veloso
 */
public interface AppDynamicsReporter {

    void publishSumValue(Meter.Id id, long value);

    void publishObservation(Meter.Id id, long value);

    void publishAggregation(Meter.Id id, long count, long value, long min, long max);

}
