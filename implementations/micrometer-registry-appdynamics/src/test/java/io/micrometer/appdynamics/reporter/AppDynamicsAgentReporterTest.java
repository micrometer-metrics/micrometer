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
package io.micrometer.appdynamics.reporter;

import com.appdynamics.agent.api.MetricPublisher;
import io.micrometer.appdynamics.AppDynamicsConfig;
import io.micrometer.appdynamics.PathNamingConvention;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.assertThat;

public class AppDynamicsAgentReporterTest {

    private final MetricPublisher publisher = mock(MetricPublisher.class);

    private final NamingConvention naming = new PathNamingConvention(AppDynamicsConfig.DEFAULT);

    private final AppDynamicsAgentReporter victim = new AppDynamicsAgentReporter(publisher, naming);

    @Test
    void testConventionName() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.OTHER);
        assertThat(victim.getConventionName(id)).isEqualTo(naming.name("test.id", Meter.Type.OTHER));
    }

    @Test
    void shouldPublishSumValue() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.OTHER);
        victim.publishSumValue(id, 10);

        verify(publisher).reportMetric(victim.getConventionName(id), 10, "SUM", "SUM", "COLLECTIVE");
    }

    @Test
    void shouldPublishAverageValue() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.OTHER);
        victim.publishAggregation(id, 3, 50, 20, 100);

        verify(publisher).reportMetric(victim.getConventionName(id), 50, 3, 20, 100, "AVERAGE", "AVERAGE",
                "INDIVIDUAL");
    }

    @Test
    void shouldPublishObservationValue() {
        Meter.Id id = new Meter.Id("test.id", Tags.empty(), null, null, Meter.Type.OTHER);
        victim.publishObservation(id, 10);

        verify(publisher).reportMetric(victim.getConventionName(id), 10, "OBSERVATION", "CURRENT", "COLLECTIVE");
    }

}
