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
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.NavigableSet;
import java.util.TreeSet;

@Incubating(since = "1.0.0-rc.3")
public class HistogramConfig implements Mergeable<HistogramConfig> {
    public static final HistogramConfig DEFAULT = builder()
        .percentilesHistogram(false)
        .percentiles()
        .sla()
        .minimumExpectedValue(1L)
        .maximumExpectedValue(Long.MAX_VALUE)
        .histogramExpiry(Duration.ofMinutes(2))
        .histogramBufferLength(5)
        .build();
    public static final HistogramConfig NONE = builder().build();

    @Nullable
    private Boolean percentileHistogram;

    @Nullable
    private double[] percentiles;

    @Nullable
    private long[] sla;

    @Nullable
    private Long minimumExpectedValue;

    @Nullable
    private Long maximumExpectedValue;

    @Nullable
    private Duration histogramExpiry;

    @Nullable
    private Integer histogramBufferLength;

    public static Builder builder() {
        return new Builder();
    }

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
        return (percentileHistogram != null && percentileHistogram) || (sla != null && sla.length > 0);
    }

    public NavigableSet<Long> getHistogramBuckets(boolean supportsAggregablePercentiles) {
        NavigableSet<Long> buckets = new TreeSet<>();

        if (percentileHistogram != null && percentileHistogram && supportsAggregablePercentiles) {
            buckets.addAll(PercentileHistogramBuckets.buckets(this));
            buckets.add(minimumExpectedValue);
            buckets.add(maximumExpectedValue);
        }

        if (sla != null) {
            for (long slaBoundary : sla) {
                buckets.add(slaBoundary);
            }
        }

        return buckets;
    }

    @Nullable
    public Boolean isPercentileHistogram() {
        return percentileHistogram;
    }

    @Nullable
    public double[] getPercentiles() {
        return percentiles;
    }

    @Nullable
    public Long getMinimumExpectedValue() {
        return minimumExpectedValue;
    }

    @Nullable
    public Long getMaximumExpectedValue() {
        return maximumExpectedValue;
    }

    @Nullable
    public Duration getHistogramExpiry() {
        return histogramExpiry;
    }

    @Nullable
    public Integer getHistogramBufferLength() {
        return histogramBufferLength;
    }

    @Nullable
    public long[] getSlaBoundaries() {
        return sla;
    }

    public static class Builder {
        private final HistogramConfig config = new HistogramConfig();

        public Builder percentilesHistogram(@Nullable Boolean enabled) {
            config.percentileHistogram = enabled;
            return this;
        }

        public Builder percentiles(@Nullable double... percentiles) {
            config.percentiles = percentiles;
            return this;
        }

        public Builder sla(@Nullable long... sla) {
            config.sla = sla;
            return this;
        }

        public Builder minimumExpectedValue(@Nullable Long min) {
            config.minimumExpectedValue = min;
            return this;
        }

        public Builder maximumExpectedValue(@Nullable Long max) {
            config.maximumExpectedValue = max;
            return this;
        }

        public Builder histogramExpiry(@Nullable Duration expiry) {
            config.histogramExpiry = expiry;
            return this;
        }

        public Builder histogramBufferLength(@Nullable Integer bufferLength) {
            config.histogramBufferLength = bufferLength;
            return this;
        }

        public HistogramConfig build() {
            return config;
        }
    }
}
