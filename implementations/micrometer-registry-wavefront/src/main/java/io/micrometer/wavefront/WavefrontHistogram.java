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
package io.micrometer.wavefront;

import com.wavefront.sdk.entities.histograms.WavefrontHistogramImpl;
import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.lang.Nullable;

import java.util.List;

import static io.micrometer.wavefront.WavefrontConstants.WAVEFRONT_METRIC_TYPE_TAG_KEY;
import static io.micrometer.wavefront.WavefrontConstants.isWavefrontMetricType;

/**
 * Wavefront's implementation of a DistributionSummary based off of WavefrontHistogramImpl.
 *
 * @author Han Zhang
 */
public class WavefrontHistogram extends AbstractDistributionSummary {
    /**
     * The tag value that is used to identify WavefrontHistograms.
     */
    private static final String WAVEFRONT_METRIC_TYPE_TAG_VALUE = "wavefrontHistogram";

    private final WavefrontHistogramImpl delegate;

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * @param id    The identifier for a metric.
     * @return {@code true} if the id identifies a WavefrontHistogram, {@code false} otherwise.
     */
    static boolean isWavefrontHistogram(Id id) {
        return isWavefrontMetricType(id, WAVEFRONT_METRIC_TYPE_TAG_VALUE);
    }

    WavefrontHistogram(Id id, Clock clock,
                       DistributionStatisticConfig distributionStatisticConfig,
                       double scale) {
        super(id, clock, distributionStatisticConfig, scale, false);
        delegate = new WavefrontHistogramImpl(clock::wallTime);
    }

    @Override
    protected void recordNonNegative(double amount) {
        delegate.update(amount);
    }

    @Override
    public long count() {
        return delegate.getCount();
    }

    @Override
    public double totalAmount() {
        return delegate.getSum();
    }

    @Override
    public double mean() {
        return delegate.getMean();
    }

    @Override
    public double max() {
        return delegate.getMax();
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        throw new UnsupportedOperationException(
            "takeSnapshot() is not supported by WavefrontHistogram");
    }

    public List<WavefrontHistogramImpl.Distribution> flushDistributions() {
        return delegate.flushDistributions();
    }

    /**
     * Fluent builder for Wavefront histograms.
     */
    static class Builder {
        private final DistributionSummary.Builder builder;

        private Builder(String name) {
            builder = DistributionSummary
                .builder(name)
                .tag(WAVEFRONT_METRIC_TYPE_TAG_KEY, WAVEFRONT_METRIC_TYPE_TAG_VALUE);
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         * @return The Wavefront histogram builder with added tags.
         */
        public Builder tags(String... tags) {
            builder.tags(tags);
            return this;
        }

        /**
         * @param tags Tags to add to the eventual distribution summary.
         * @return The Wavefront histogram builder with added tags.
         */
        public Builder tags(Iterable<Tag> tags) {
            builder.tags(tags);
            return this;
        }

        /**
         * @param key   The tag key.
         * @param value The tag value.
         * @return The Wavefront histogram builder with a single added tag.
         */
        public Builder tag(String key, String value) {
            builder.tag(key, value);
            return this;
        }

        /**
         * @param description Description text of the eventual distribution summary.
         * @return The Wavefront histogram builder with added description.
         */
        public Builder description(@Nullable String description) {
            builder.description(description);
            return this;
        }

        /**
         * @param unit Base unit of the eventual distribution summary.
         * @return The Wavefront histogram builder with added base unit.
         */
        public Builder baseUnit(@Nullable String unit) {
            builder.baseUnit(unit);
            return this;
        }

        /**
         * Add the Wavefront histogram to a single registry, or return an existing
         * Wavefront histogram in that registry. The returned distribution summary
         * will be unique for each registry, but each registry is guaranteed to only
         * create one distribution summary for the same combination of name and tags.
         * If an existing distribution summary is found but it is not a Wavefront histogram,
         * an IllegalStateException is thrown.
         *
         * @param registry A registry to add the Wavefront histogram to, if it doesn't already exist.
         * @return A new or existing Wavefront histogram.
         */
        public WavefrontHistogram register(MeterRegistry registry) {
            DistributionSummary summary = builder.register(registry);
            if (summary instanceof WavefrontHistogram) {
                return (WavefrontHistogram) summary;
            } else {
                throw new IllegalStateException("Found existing non-WavefrontHistogram: " + summary);
            }
        }
    }
}