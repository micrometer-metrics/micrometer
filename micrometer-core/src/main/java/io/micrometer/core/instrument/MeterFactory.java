package io.micrometer.core.instrument;

import io.micrometer.core.instrument.noop.NoOpRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

public class MeterFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(MeterFactory.class);

  private static AtomicReference<MeterRegistry> registry = new AtomicReference<>(new SimpleMeterRegistry());

  public static Counter counter(String name, String... tags){
    return registry.get().counter(name, tags);
  }

  public static LongTaskTimer longTaskTimer(String name, String... tags){
    return registry.get().longTaskTimer(name, tags);
  }

  public static DistributionSummary summary(String name, String... tags){
    return registry.get().summary(name, tags);
  }

  public static Timer timer(String name, String... tags){
    return registry.get().timer(name, tags);
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
    registry.get().register(meter);
    return meter;
  }

  public static void setRegistry(MeterRegistry newRegistry){
    if(newRegistry == null) {
      throw new NullPointerException("Null Registry is not allowed. Use a "+ NoOpRegistry.class.getCanonicalName()+" instead");
    }
    registry.set(newRegistry);
  }

  public static MeterRegistry registry() {
    return registry.get();
  }

}
