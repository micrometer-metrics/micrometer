package org.springframework.metrics.instrument.scheduling;

import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.binder.MeterBinder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorServiceMetrics implements MeterBinder {

  private final String name;
  private final ExecutorService executorService;

  public ExecutorServiceMetrics(String name, ExecutorService executorService) {
    this.name = name;
    this.executorService = executorService;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    if(executorService instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor tp = (ThreadPoolExecutor) executorService;
      registry.gauge(name+"_scheduled_total", tp, ThreadPoolExecutor::getTaskCount); //Really should be a counter
      registry.gauge(name+"_completed_total", tp, ThreadPoolExecutor::getCompletedTaskCount); //Really should be a counter
      registry.gauge(name+"_active_count", tp, ThreadPoolExecutor::getActiveCount);
      registry.gauge(name+"_queue_size", tp, (i) -> i.getQueue().size());
      registry.gauge(name+"_pool_size", tp, ThreadPoolExecutor::getPoolSize);
    }
  }
}
