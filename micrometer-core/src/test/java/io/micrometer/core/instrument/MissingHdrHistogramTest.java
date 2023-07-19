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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.testsupport.classpath.ClassPathExclusions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for demonstrating that a timer works without HdrHistogram dependency if
 * percentiles are not used.
 *
 * @author Johnny Lim
 */
@ClassPathExclusions("HdrHistogram-*.jar")
class MissingHdrHistogramTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void doesNotThrowAnyExceptionWhenPercentilesAreNotUsed() {
        assertThatCode(() -> Timer.builder("my.timer").register(registry)).doesNotThrowAnyException();
    }

    @Test
    void throwsClassNotFoundExceptionWhenPercentilesAreUsed() {
        assertThatThrownBy(() -> Timer.builder("my.timer").publishPercentiles(0.5d, 0.9d).register(registry))
            .hasCauseExactlyInstanceOf(ClassNotFoundException.class);
    }

}
