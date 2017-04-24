package org.springframework.metrics.collector;

/**
 * Track the sample distribution of events. An example would be the response sizes for requests
 * hitting and http server.
 */
public interface DistributionSummary extends Meter {

  /**
   * Updates the statistics kept by the summary with the specified amount.
   *
   * @param amount
   *     Amount for an event being measured. For example, if the size in bytes of responses
   *     from a server. If the amount is less than 0 the value will be dropped.
   */
  void record(long amount);

  /** The number of times that record has been called since this timer was created. */
  long count();

  /** The total amount of all recorded events since this summary was created. */
  long totalAmount();
}
