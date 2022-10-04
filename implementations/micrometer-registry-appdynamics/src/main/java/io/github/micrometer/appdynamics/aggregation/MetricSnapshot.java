package io.github.micrometer.appdynamics.aggregation;

/**
 * @author Ricardo Veloso
 */
public class MetricSnapshot {

    private final long count;

    private final double min;

    private final double max;

    private final double total;

    public MetricSnapshot(long count, double min, double max, double total) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.total = total;
    }

    public long count() {
        return count;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double total() {
        return total;
    }

}
