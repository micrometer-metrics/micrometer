/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.config.InvalidConfigurationException;
import io.micrometer.core.instrument.distribution.HdrHistogramAvailability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.classpath.ClassPathExclusions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for demonstrating that meters work without the optional HdrHistogram dependency
 * unless client-side percentiles are used, in which case a descriptive error is raised.
 *
 * @author Johnny Lim
 * @author Rafael Winterhalter
 */
@ClassPathExclusions("HdrHistogram-*.jar")
class MissingHdrHistogramTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void reportsHdrHistogramAsUnavailable() {
        assertThat(HdrHistogramAvailability.isAvailable()).isFalse();
    }

    @Test
    void doesNotThrowAnyExceptionWhenPercentilesAreNotUsed() {
        assertThatCode(() -> Timer.builder("my.timer").register(registry)).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowAnyExceptionForHistogramsAndServiceLevelObjectives() {
        assertThatCode(() -> Timer.builder("my.histogram.timer").publishPercentileHistogram().register(registry))
            .doesNotThrowAnyException();
        assertThatCode(
                () -> DistributionSummary.builder("my.slo.summary").serviceLevelObjectives(1.0, 2.0).register(registry))
            .doesNotThrowAnyException();
    }

    @Test
    void throwsDescriptiveExceptionWhenTimerPercentilesAreUsed() {
        assertThatThrownBy(() -> Timer.builder("my.timer").publishPercentiles(0.5d, 0.9d).register(registry))
            .isInstanceOf(InvalidConfigurationException.class)
            .hasMessageContaining("HdrHistogram")
            .hasMessageContaining("org.hdrhistogram:HdrHistogram")
            .hasMessageContaining("not on the runtime classpath");
    }

    @Test
    void throwsDescriptiveExceptionWhenDistributionSummaryPercentilesAreUsed() {
        assertThatThrownBy(
                () -> DistributionSummary.builder("my.summary").publishPercentiles(0.5d, 0.9d).register(registry))
            .isInstanceOf(InvalidConfigurationException.class)
            .hasMessageContaining("org.hdrhistogram:HdrHistogram");
    }

}
