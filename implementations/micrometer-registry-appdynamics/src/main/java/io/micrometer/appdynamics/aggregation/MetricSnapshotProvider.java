package io.micrometer.appdynamics.aggregation;

import java.util.concurrent.TimeUnit;

/**
 * @author Ricardo Veloso
 */
public interface MetricSnapshotProvider {

    MetricSnapshot snapshot();

    MetricSnapshot snapshot(TimeUnit unit);

}
