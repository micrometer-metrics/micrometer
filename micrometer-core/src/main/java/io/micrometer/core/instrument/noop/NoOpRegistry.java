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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class NoOpRegistry implements MeterRegistry {
  private static final Counter NOOP_COUNTER = new NoOpCounter();
  private static final DistributionSummary NOOP_DISTRUBUTION_SUMMARY = new NoOpDistributionSummary();
  private static final DistributionSummary.Builder NOOP_DISTRUBUTION_SUMMARY_BUILDER = new NoOpDistributionSummary.Builder();
  private static final LongTaskTimer NOOP_LONG_TASK_TIMER = new NoOpLongTaskTimer();
  private static final Timer NOOP_TIMER = new NoOpTimer();
  private static final Timer.Builder NOOP_TIMER_BUILDER = new NoOpTimer.Builder();

  @SuppressWarnings("unchecked")
  @Override
  public Collection<Meter> getMeters() {
    return Collections.EMPTY_LIST;
  }

  @Override
  public void commonTags(Iterable<Tag> tags) {
    //No op
  }

  @Override
  public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
    return Optional.empty();
  }

  @Override
  public Optional<Meter> findMeter(Meter.Type type, String name, Iterable<Tag> tags) {
    return Optional.empty();
  }

  @Override
  public Clock getClock() {
    return Clock.SYSTEM;
  }

  @Override
  public Counter counter(String name, Iterable<Tag> tags) {
    return NOOP_COUNTER;
  }

  @Override
  public DistributionSummary.Builder summaryBuilder(String name) {
    return NOOP_DISTRUBUTION_SUMMARY_BUILDER;
  }

  @Override
  public Timer.Builder timerBuilder(String name) {
    return NOOP_TIMER_BUILDER;
  }

  @Override
  public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
    return NOOP_LONG_TASK_TIMER;
  }

  @Override
  public Meter meter(Meter meter) {
    return meter;
  }

  @Override
  public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
    return obj;
  }


  public static class NoOpTimer extends NoOpMeter implements Timer {

    @Override
    public void record(long amount, TimeUnit unit) {
      //NO OP
    }

    @Override
    public <T> T record(Supplier<T> f) {
      return f.get();
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
      return f.call();
    }

    @Override
    public void record(Runnable f) {
      f.run();
    }

    @Override
    public <T> Callable<T> wrap(Callable<T> f) {
      return f;
    }

    @Override
    public long count() {
      return 0;
    }

    @Override
    public double totalTime(TimeUnit unit) {
      return 0;
    }

    public static class Builder implements Timer.Builder {

      @Override
      public Timer.Builder quantiles(Quantiles quantiles) {
        return this;
      }

      @Override
      public Timer.Builder histogram(Histogram<?> histogram) {
        return this;
      }

      @Override
      public Timer.Builder tags(Iterable<Tag> tags) {
        return this;
      }

      @Override
      public Timer create() {
        return NOOP_TIMER;
      }
    }
  }


  public static class NoOpLongTaskTimer extends NoOpMeter implements LongTaskTimer {

    @Override
    public long start() {
      return 0;
    }

    @Override
    public long stop(long task) {
      return 0;
    }

    @Override
    public long duration(long task) {
      return 0;
    }

    @Override
    public long duration() {
      return 0;
    }

    @Override
    public int activeTasks() {
      return 0;
    }
  }

  public static class NoOpDistributionSummary extends NoOpMeter implements DistributionSummary {

    @Override
    public void record(double amount) {
      //NO OP
    }

    @Override
    public long count() {
      return 0;
    }

    @Override
    public double totalAmount() {
      return 0;
    }

    public static class Builder implements DistributionSummary.Builder {

      @Override
      public DistributionSummary.Builder quantiles(Quantiles quantiles) {
        return this;
      }

      @Override
      public DistributionSummary.Builder histogram(Histogram<?> histogram) {
        return this;
      }

      @Override
      public DistributionSummary.Builder tags(Iterable<Tag> tags) {
        return this;
      }

      @Override
      public DistributionSummary create() {
        return NOOP_DISTRUBUTION_SUMMARY;
      }
    }
  }




  public static class NoOpCounter extends NoOpMeter implements Counter{
    @Override
    public void increment() {
      //NO OP
    }

    @Override
    public void increment(double amount) {
      //NO OP
    }

    @Override
    public double count() {
      return 0;
    }
  }

  public static abstract class NoOpMeter implements Meter{

    @Override
    public String getName() {
      return "NO OP";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<Tag> getTags() {
      return Collections.EMPTY_LIST;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Measurement> measure() {
      return Collections.EMPTY_LIST;
    }
  }
}
