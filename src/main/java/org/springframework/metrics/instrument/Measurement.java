package org.springframework.metrics.instrument;

import org.springframework.metrics.instrument.internal.MeterId;

/**
 * A measurement sampled from a meter.
 */
public final class Measurement {

  private final MeterId id;
  private final double value;

  /** Create a new instance. */
  public Measurement(MeterId id, double value) {
    this.id = id;
    this.value = value;
  }

  /** Identifier for the measurement. */
  public MeterId id() {
    return id;
  }

  /** Value for the measurement. */
  public double value() {
    return value;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || !(obj instanceof Measurement)) return false;
    Measurement other = (Measurement) obj;
    return id.equals(other.id)
            && Double.compare(value, other.value) == 0;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int hc = prime;
    hc = prime * hc + id.hashCode();
    hc = prime * hc + Double.valueOf(value).hashCode();
    return hc;
  }

  @Override
  public String toString() {
    return "Measurement(" + id.toString() + "," + value + ")";
  }
}
