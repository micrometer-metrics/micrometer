package org.springframework.metrics.instrument;

import java.util.Arrays;

/**
 * A measurement sampled from a meter.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 */
public final class Measurement {

    private final String name;
    private final Tag[] tags;
    private final double value;

    /**
     * Create a new instance.
     */
    public Measurement(String name, Tag[] tags, double value) {
        this.name = name;
        this.tags = tags;
        this.value = value;
    }

    /**
     * Name of the measurement, which together with tags form a unique time series.
     */
    public String getName() {
        return name;
    }

    /**
     * Tags for the measurement, which together with name form a unique time series.
     */
    public Tag[] getTags() { return tags; }

    /**
     * Value for the measurement.
     */
    public double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Measurement that = (Measurement) o;

        if (Double.compare(that.value, value) != 0) return false;
        if (!name.equals(that.name)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = name.hashCode();
        result = 31 * result + Arrays.hashCode(tags);
        temp = Double.doubleToLongBits(value);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "Measurement{" +
                "name='" + name + '\'' +
                ", tags=" + Arrays.toString(tags) +
                ", value=" + value +
                '}';
    }
}
