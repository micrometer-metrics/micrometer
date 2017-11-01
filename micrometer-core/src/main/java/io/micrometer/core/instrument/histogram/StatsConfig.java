/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.histogram;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;

public class StatsConfig {
    private boolean percentileHistogram = false;
    private double[] percentiles = new double[0];
    private long[] sla = new long[0];
    private long minimumExpectedValue = 1;
    private long maximumExpectedValue = Long.MAX_VALUE;

    /**
     * Set the duration of the time window is, i.e. how long observations are kept before they are discarded.
     * Monitoring systems that publish should set the default max age to the publish interval.
     */
    private Duration maxAge = Duration.ofMinutes(1);

    /**
     * Set the number of buckets used to implement the sliding time window. If your time window is 10 minutes, and you have ageBuckets=2,
     * buckets will be switched every 5 minutes. The value is a trade-off between resources (memory and cpu for maintaining the bucket)
     * and how smooth the time window is moved.
     */
    private int ageBuckets = 3;

    public boolean isPublishingHistogram() {
        return percentileHistogram || sla.length > 0;
    }

    public NavigableSet<Long> getHistogramBuckets(boolean supportsAggregablePercentiles) {
        NavigableSet<Long> buckets = new TreeSet<>();

        if(percentileHistogram && supportsAggregablePercentiles) {
            buckets.addAll(PercentileHistogramBuckets.buckets(this));
            buckets.add(minimumExpectedValue);
            buckets.add(maximumExpectedValue);
        }

        for (long slaBoundary : sla) {
            buckets.add(slaBoundary);
        }

        return buckets;
    }

    public boolean isPercentileHistogram() {
        return percentileHistogram;
    }

    public double[] getPercentiles() {
        return percentiles;
    }

    public long getMinimumExpectedValue() {
        return minimumExpectedValue;
    }

    public long getMaximumExpectedValue() {
        return maximumExpectedValue;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public int getAgeBuckets() {
        return ageBuckets;
    }

    public long[] getSlaBoundaries() {
        return sla;
    }

    public void setPercentileHistogram(boolean percentileHistogram) {
        this.percentileHistogram = percentileHistogram;
    }

    public void setPercentiles(double[] percentiles) {
        this.percentiles = percentiles;
    }

    public void setSlaBoundaries(long[] sla) {
        this.sla = sla;
    }

    public void setMinimumExpectedValue(long minimumExpectedValue) {
        this.minimumExpectedValue = minimumExpectedValue;
    }

    public void setMaximumExpectedValue(long maximumExpectedValue) {
        this.maximumExpectedValue = maximumExpectedValue;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }

    public void setAgeBuckets(int ageBuckets) {
        this.ageBuckets = ageBuckets;
    }
}
