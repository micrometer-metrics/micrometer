package org.springframework.metrics.instrument.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.metrics.instrument.Gauge;
import org.springframework.metrics.instrument.Meter;
import org.springframework.metrics.instrument.MeterRegistriesProvider;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ExecutorServiceMetricsTest {

  @DisplayName("tp task executor can be instrumented after being initialized")
  @ParameterizedTest
  @ArgumentsSource(MeterRegistriesProvider.class)
  void taskExecutor(MeterRegistry registry) {
    ThreadPoolTaskExecutor sched = new ThreadPoolTaskExecutor();

    // Task decorator must be set before calling initialize.
    sched.setTaskDecorator(f -> registry.timer("my_tp_executor_duration").wrap(f));
    sched.initialize();

    // Binder must be added after calling initialize (likely via a Spring post processor).
    registry.bind(new ExecutorServiceMetrics("my_tp_executor", sched.getThreadPoolExecutor()));

    Optional<Meter> my_tp_executor_scheduled_total = registry.getMeters().stream().filter(m -> m.getName().equals("my_tp_executor_scheduled_total")).findAny();
    assertThat(my_tp_executor_scheduled_total).isPresent();
    assertThat(my_tp_executor_scheduled_total.get()).isInstanceOf(Gauge.class);

  }

  @DisplayName("tp task scheduler can be instrumented after being initialized")
  @ParameterizedTest
  @ArgumentsSource(MeterRegistriesProvider.class)
  void taskScheduler(MeterRegistry registry) {
    ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
    sched.initialize();

    registry.bind(new ExecutorServiceMetrics("my_tp_scheduler", sched.getScheduledThreadPoolExecutor()));

    Optional<Meter> my_tp_executor_scheduled_total = registry.getMeters().stream().filter(m -> m.getName().equals("my_tp_scheduler_scheduled_total")).findAny();
    assertThat(my_tp_executor_scheduled_total).isPresent();
    assertThat(my_tp_executor_scheduled_total.get()).isInstanceOf(Gauge.class);

  }

}
