package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NoOpRegistryTest {

  @Test
  @DisplayName("no op registry uses same instance to NoOp counters to minimize memory impact")
  void noopMeter() {
    NoOpRegistry registry = new NoOpRegistry();

    Counter a = registry.counter("foo");
    a.increment();
    Counter b = registry.counter("foo2");
    b.increment(10D);
    assertThat(a).isSameAs(b);

    assertThat(registry.getMeters().size())
            .as("NoOpRegistry is always empty")
            .isEqualTo(0);
  }

  @Test
  @DisplayName("no op registry timer calls runnables/callables")
  void noopMeterTimers() throws Exception {
    NoOpRegistry registry = new NoOpRegistry();

    AtomicInteger verifier = new AtomicInteger();
    Runnable runnable = verifier::incrementAndGet;
    Supplier<Object> supplier = verifier::incrementAndGet;
    Callable<Object> callable = verifier::incrementAndGet;
    Consumer<Long> consumer = (Long val) -> verifier.incrementAndGet();

    registry.longTaskTimer("foo").record(runnable);
    registry.longTaskTimer("foo").record(supplier);
    registry.longTaskTimer("foo").recordCallable(callable);
    registry.longTaskTimer("foo").record(consumer);

    registry.timerBuilder("noop").create().record(runnable);
    registry.timerBuilder("noop").create().record(supplier);
    registry.timerBuilder("noop").create().recordCallable(callable);

    assertThat(verifier.get()).isEqualTo(7);
  }

}
