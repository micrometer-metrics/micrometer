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
package io.micrometer.registry.otlp.internal;

import java.util.HashMap;
import java.util.Map;

/**
 * A factory that provides the {@link IndexProvider} for a given scale.
 *
 * @author Lenin Jaganathan
 * @since 1.14.0
 * @see <a href=
 * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/data-model.md#exponential-buckets">Exponentioal
 * Buckets</a>
 */
class IndexProviderFactory {

    private static final Map<Integer, IndexProvider> indexProviderCache = new HashMap<>();

    private static final IndexProvider ZERO_SCALE_INDEX_PROVIDER = new ZeroScaleIndexProvider();

    private IndexProviderFactory() {
    }

    static IndexProvider getIndexProviderForScale(int scale) {
        if (scale > 0) {
            return indexProviderCache.computeIfAbsent(scale, PositiveScaleIndexProvider::new);
        }
        else if (scale < 0) {
            return indexProviderCache.computeIfAbsent(scale, NegativeScaleIndexProvider::new);
        }
        return ZERO_SCALE_INDEX_PROVIDER;
    }

    /**
     * Use <a href=
     * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/data-model.md#all-scales-use-the-logarithm-function">Use
     * the Logarithm Function </a> to calculate index for positive scale.
     */
    private static class PositiveScaleIndexProvider implements IndexProvider {

        private final double scaleFactor;

        PositiveScaleIndexProvider(int scale) {
            this.scaleFactor = Math.scalb(Math.log(Math.E) / Math.log(2), scale);
        }

        @Override
        public int getIndexForValue(final double value) {
            // NOTE: Is it worth handling the mapping of exact powers of 2 as mentioned in
            // the spec?
            return (int) Math.ceil(Math.log(value) * scaleFactor) - 1;
        }

    }

    /**
     * Use <a href=
     * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/data-model
     * .md#scale-zero-extract-the-exponent"> Extract the Exponent </a> to calculate index
     * for zero scale.
     */
    private static class ZeroScaleIndexProvider implements IndexProvider {

        // IEEE 754 double-precision constants
        private static final long SIGNIFICAND_MASK = 0x000FFFFFFFFFFFFFL;

        private static final long EXPONENT_MASK = 0x7FF0000000000000L;

        private static final int SIGNIFICAND_WIDTH = 52;

        private static final int EXPONENT_BIAS = 1023;

        ZeroScaleIndexProvider() {
        }

        @Override
        public int getIndexForValue(final double value) {
            long rawBits = Double.doubleToLongBits(value);
            long rawExponent = (rawBits & EXPONENT_MASK) >> SIGNIFICAND_WIDTH;
            long rawFragment = rawBits & SIGNIFICAND_MASK;
            if (rawExponent == 0) {
                rawExponent -= Long.numberOfLeadingZeros(rawFragment - 1) - 12;
            }
            int ieeeExponent = (int) (rawExponent - EXPONENT_BIAS);

            if (rawFragment == 0) {
                return ieeeExponent - 1;
            }
            return ieeeExponent;
        }

    }

    /**
     * Use <a href=
     * "https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/data-model.md#negative-scale-extract-and-shift-the-exponent">Index
     * computation for negative scale</a> to calculate index for negative scale.
     */
    private static class NegativeScaleIndexProvider implements IndexProvider {

        private final int scale;

        private NegativeScaleIndexProvider(final int scale) {
            this.scale = scale;
        }

        @Override
        public int getIndexForValue(final double value) {
            return ZERO_SCALE_INDEX_PROVIDER.getIndexForValue(value) >> -scale;
        }

    }

}
