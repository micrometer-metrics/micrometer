package org.springframework.metrics.instrument;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Timer intended to track a large number of short running events. Example would be something like
 * an http request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 */
public interface Timer extends Meter {
  /**
   * Updates the statistics kept by the counter with the specified amount.
   *
   * @param amount Duration of a single event being measured by this timer. If the amount is less than 0
   *               the value will be dropped.
   * @param unit Time unit for the amount being recorded.
   */
  void record(long amount, TimeUnit unit);

  /**
   * Executes the callable `f` and records the time taken.
   *
   * @param f Function to execute and measure the execution time.
   * @return The return value of `f`.
   */
  <T> T record(Callable<T> f) throws Exception;

  /**
   * Executes the runnable `f` and records the time taken.
   *
   * @param f Function to execute and measure the execution time.
   */
  void record(Runnable f);

  /** The number of times that record has been called since this timer was created. */
  long count();

  /** The total time of all recorded events since this timer was created. */
  double totalTime(TimeUnit unit);

  default double totalTimeNanos() {
    return totalTime(TimeUnit.NANOSECONDS);
  }
}
