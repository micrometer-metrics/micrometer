package org.springframework.metrics.export.datadog;

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
