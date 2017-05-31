package org.springframework.metrics.instrument;

import org.springframework.metrics.instrument.binder.MeterBinder;

import java.util.List;

abstract public class MeterReporter implements MeterBinder{

  @Override
  public void bindTo(MeterRegistry registry) {
    registry.monitor(this);
  }

  abstract public List<MeterSamples> report();
}
