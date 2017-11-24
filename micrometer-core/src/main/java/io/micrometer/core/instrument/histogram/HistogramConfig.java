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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.internal.Mergeable;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;

@Incubating(since = "1.0.0-rc.3")
public class HistogramConfig implements Mergeable<HistogramConfig> {
    private Boolean percentileHistogram;
    private double[] percentiles;
    private long[] sla;
    private Long minimumExpectedValue;
    private Long maximumExpectedValue;

    private Duration histogramExpiry;
    private Integer histogramBufferLength;

    public static final HistogramConfig DEFAULT = builder()
        .percentilesHistogram(false)
        .percentiles()
        .sla()
        .minimumExpectedValue(1L)
        .maximumExpectedValue(Long.MAX_VALUE)
        .histogramExpiry(Duration.ofMinutes(2))
        .histogramBufferLength(5)
        .build();

    @Override
    public HistogramConfig merge(HistogramConfig parent) {
        return HistogramConfig.builder()
            .percentilesHistogram(this.percentileHistogram == null ? parent.percentileHistogram : this.percentileHistogram)
            .percentiles(this.percentiles == null ? parent.percentiles : this.percentiles)
            .sla(this.sla == null ? parent.sla : this.sla)
            .minimumExpectedValue(this.minimumExpectedValue == null ? parent.minimumExpectedValue : this.minimumExpectedValue)
            .maximumExpectedValue(this.maximumExpectedValue == null ? parent.maximumExpectedValue : this.maximumExpectedValue)
            .histogramExpiry(this.histogramExpiry == null ? parent.histogramExpiry : this.histogramExpiry)
            .histogramBufferLength(this.histogramBufferLength == null ? parent.histogramBufferLength : this.histogramBufferLength)
            .build();
    }

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

    public Boolean isPercentileHistogram() {
        return percentileHistogram;
    }

    public double[] getPercentiles() {
        return percentiles;
    }

    public Long getMinimumExpectedValue() {
        return minimumExpectedValue;
    }

    public Long getMaximumExpectedValue() {
        return maximumExpectedValue;
    }

    public Duration getHistogramExpiry() {
        return histogramExpiry;
    }

    public Integer getHistogramBufferLength() {
        return histogramBufferLength;
    }

    public long[] getSlaBoundaries() {
        return sla;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final HistogramConfig config = new HistogramConfig();

        public Builder percentilesHistogram(Boolean enabled) {
            config.percentileHistogram = enabled;
            return this;
        }

        public Builder percentiles(double... percentiles) {
            config.percentiles = percentiles;
            return this;
        }

        public Builder sla(long... sla) {
            config.sla = sla;
            return this;
        }

        public Builder minimumExpectedValue(Long min) {
            config.minimumExpectedValue = min;
            return this;
        }

        public Builder maximumExpectedValue(Long max) {
            config.maximumExpectedValue = max;
            return this;
        }

        public Builder histogramExpiry(Duration expiry) {
            config.histogramExpiry = expiry;
            return this;
        }

        public Builder histogramBufferLength(Integer bufferLength) {
            config.histogramBufferLength = bufferLength;
            return this;
        }

        public HistogramConfig build() {
            return config;
        }
    }
}
