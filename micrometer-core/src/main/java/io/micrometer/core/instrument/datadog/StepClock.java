/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.datadog;

import com.netflix.spectator.api.Clock;

/**
 * Wraps a clock implementation with one that only reports wall times on
 * exact boundaries of the step. This is used so that measurements sampled
 * from gauges will all have the same timestamp for a given reporting
 * interval.
 */
class StepClock implements Clock {

  private final Clock impl;
  private final long step;

  /** Create a new instance. */
  StepClock(Clock impl, long step) {
    this.impl = impl;
    this.step = step;
  }

  @Override
  public long wallTime() {
    return impl.wallTime() / step * step;
  }

  @Override
  public long monotonicTime() {
    return impl.monotonicTime();
  }
}
