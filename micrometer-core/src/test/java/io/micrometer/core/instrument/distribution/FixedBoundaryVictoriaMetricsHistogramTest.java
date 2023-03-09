/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.Issue;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FixedBoundaryVictoriaMetricsHistogramTest {

    @Test
    void checkUpperBoundLookup() {
        try (FixedBoundaryVictoriaMetricsHistogram histogram = new FixedBoundaryVictoriaMetricsHistogram()) {
            assertThat(histogram.getRangeTagValue(0.0d)).isEqualTo("0...0");
            assertThat(histogram.getRangeTagValue(1e-9d)).isEqualTo("0...1.0e-9");
            assertThat(histogram.getRangeTagValue(Double.POSITIVE_INFINITY)).isEqualTo("1.0e18...+Inf");
            assertThat(histogram.getRangeTagValue(1e18d)).isEqualTo("9.5e17...1.0e18");
        }
    }

    @Test
    @Issue("#3676")
    void localeDoesNotChangeOutput() {
        Locale defaultLocale = Locale.getDefault();
        try {
            // set a Locale that uses a comma for the decimal separator
            Locale.setDefault(Locale.FRANCE);
            try (FixedBoundaryVictoriaMetricsHistogram histogram = new FixedBoundaryVictoriaMetricsHistogram()) {
                assertThat(histogram.getRangeTagValue(0.0d)).isEqualTo("0...0");
                assertThat(histogram.getRangeTagValue(1e-9d)).isEqualTo("0...1.0e-9");
                assertThat(histogram.getRangeTagValue(Double.POSITIVE_INFINITY)).isEqualTo("1.0e18...+Inf");
                assertThat(histogram.getRangeTagValue(1e18d)).isEqualTo("9.5e17...1.0e18");
            }
        }
        finally {
            Locale.setDefault(defaultLocale);
        }
    }

}
