/*
 * Copyright 2023 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheusnative;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.prometheus.metrics.core.metrics.SlidingWindow;

/**
 * Micrometer distributions have a {@code max} value, which is not provided out-of-the-box
 * by Prometheus histograms or summaries.
 * <p>
 * Max is used to track these {@code max} values.
 */
public class Max {

    private static class MaxObserver {

        private double current = Double.NaN;

        public void observe(double value) {
            if (Double.isNaN(current) || current < value) {
                current = value;
            }
        }

        public double get() {
            return current;
        }

    }

    private final SlidingWindow<MaxObserver> slidingWindow;

    public Max(DistributionStatisticConfig config) {
        long maxAgeSeconds = config.getExpiry() != null ? config.getExpiry().toMillis() / 1000L : 60;
        int ageBuckets = config.getBufferLength() != null ? config.getBufferLength() : 3;
        slidingWindow = new SlidingWindow<>(MaxObserver.class, MaxObserver::new, MaxObserver::observe, maxAgeSeconds,
                ageBuckets);
    }

    public void observe(double value) {
        slidingWindow.observe(value);
    }

    public double get() {
        return slidingWindow.current().get();
    }

}
