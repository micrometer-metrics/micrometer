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

package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DropwizardGauge}
 *
 * @author Oleksii Bondar
 */
class DropwizardGaugeTest {

    Meter.Id id = new Meter.Id("name", Tags.empty(), null, null, Meter.Type.GAUGE);

    @Test
    void returnNonNullValue() {
        double expectedValue = 10d;
        DropwizardGauge gauge = new DropwizardGauge(id, new Gauge<Double>() {

            @Override
            public Double getValue() {
                return expectedValue;
            }
        });
        assertThat(gauge.value()).isEqualTo(expectedValue);
    }

    @Test
    void returnNanForNullValue() {
        DropwizardGauge gauge = new DropwizardGauge(id, new Gauge<Double>() {

            @Override
            public @Nullable Double getValue() {
                return null;
            }
        });
        assertThat(gauge.value()).isNaN();
    }

}
