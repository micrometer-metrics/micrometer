package io.micrometer.core.api;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.MapAccess;
import io.micrometer.core.instrument.util.MeterId;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

public class SwappableMeterRegistry extends SimpleMeterRegistry{
  private final ConcurrentMap<MeterId, Meter> meterMap = new ConcurrentHashMap<>();

  @Override
  public Counter counter(String name, Iterable<Tag> tags) {
    return MapAccess.computeIfAbsent(meterMap, new MeterId(name, withCommonTags(tags)), (id) -> {
      Counter counter =super.counter(name, tags);
      return new DelegatingCounter(counter);
    });
  }

  @Override
  public DistributionSummary.Builder summaryBuilder(String name) {
    return null;
  }

  @Override
  public Timer.Builder timerBuilder(String name) {
    return null;
  }

  @Override
  public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
    return null;
  }


  @Override
  public Meter meter(Meter meter) {
    return null;
  }

  void swapWith(MeterRegistry newRegistry) {
    for(Map.Entry<MeterId, Meter> entry : meterMap.entrySet()) {
      if(entry.getValue() instanceof DelegatingCounter) {
        DelegatingCounter dCounter = ((DelegatingCounter)entry.getValue());
        dCounter.setDelegate(newRegistry.counter(dCounter.getName(), dCounter.getTags()));
      }
    }
  }


  public static class DelegatingCounter implements Counter {
    private AtomicReference<Counter> delegate = new AtomicReference<>();

    public DelegatingCounter(Counter delegate) {
      this.delegate.set(delegate);
    }

    @Override
    public void increment() {
      delegate.get().increment();
    }

    @Override
    public void increment(double amount) {
      delegate.get().increment(amount);
    }

    @Override
    public double count() {
      return delegate.get().count();
    }

    @Override
    public String getName() {
      return delegate.get().getName();
    }

    @Override
    public Iterable<Tag> getTags() {
      return delegate.get().getTags();
    }

    @Override
    public List<Measurement> measure() {
      return delegate.get().measure();
    }

    public void setDelegate(Counter newCounter) {
      Counter old = delegate.get();
      newCounter.increment(old.count());
      delegate.set(newCounter);
    }
  }

}
