package org.springframework.metrics.instrument;

/**
 * Measures the rate of change based on calls to increment.
 */
public interface Counter extends Meter {
  /** Update the counter by one. */
  void increment();

  /**
   * Update the counter by {@code amount}.
   *
   * @param amount
   *     Amount to add to the counter.
   */
  void increment(double amount);

  /** The cumulative count since this counter was created. */
  double count();
}
