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
package io.micrometer.core.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoOpRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

public class MeterFactory {
  public static Counter counter(String name, String... tags){
    return Config.registry.get().counter(name, tags);
  }

  public static LongTaskTimer longTaskTimer(String name, String... tags){
    return Config.registry.get().longTaskTimer(name, tags);
  }

  public static DistributionSummary summary(String name, String... tags){
    return Config.registry.get().summary(name, tags);
  }

  public static Timer timer(String name, String... tags){
    return Config.registry.get().timer(name, tags);
  }

//  public Gauge gauge(String name, DoubleSupplier gaugeProvider, String... tags){
//    return registry.get().gauge(name, Tags.zip(tags), 1.0, new ToDoubleFunction<Double>() {
//      @Override
//      public double applyAsDouble(Double value) {
//        return gaugeProvider.getAsDouble();
//      }
//    });
//  }

  public static Meter meter(Meter meter){
    Config.registry.get().meter(meter);
    return meter;
  }

  public static class Config {
    private static AtomicReference<MeterRegistry> registry = new AtomicReference<>(new SwappableMeterRegistry());

    public static void setRegistry(MeterRegistry newRegistry){
      if(newRegistry == null) {
        throw new NullPointerException("Null Registry is not allowed. Use a "+ NoOpRegistry.class.getCanonicalName()+" instead");
      }
      if(registry.get() instanceof SwappableMeterRegistry) {
        ((SwappableMeterRegistry)registry.get()).swapWith(newRegistry);
      }
      registry.set(newRegistry);
    }

    public static MeterRegistry registry() {
      return registry.get();
    }
  }

}
