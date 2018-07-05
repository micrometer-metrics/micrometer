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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppOpticsMeterRegistryTest {

    private final static AppOpticsConfig mockConfig = mock(AppOpticsConfig.class);
    private final static Clock mockClock = mock(Clock.class);
    private final static ThreadFactory mockThreadFactory = mock(ThreadFactory.class);

    static {
        when(mockConfig.metricPrefix()).thenReturn("prefix");
    }
    private final static AppOpticsMeterRegistry registry = new AppOpticsMeterRegistry(
        mockConfig, mockClock, mockThreadFactory
    );

    private final static String mockPrefix = "prefix.";

    private final String mockName = "Mock Name";
    private final double mockValue = 123.123;
    private final double mockSum = 321.321;
    private final long mockCount = 123;
    private final double mockMax = 432.432;

    private final AggregateMeasurement expectedAggregate = AggregateMeasurement.newBuilder()
        .withName(mockPrefix + mockName)
        .withSum(mockSum)
        .withCount(mockCount)
        .withMax(mockMax)
        .withTags(new ArrayList())
        .build();

    final AggregateMeasurement expectedAggregateNoMax = AggregateMeasurement.newBuilder()
        .withName(mockPrefix + mockName)
        .withSum(mockSum)
        .withCount(mockCount)
        .withTags(new ArrayList())
        .build();

    final SingleMeasurement expectedSingle = SingleMeasurement.newBuilder()
        .withName(mockPrefix + mockName)
        .withValue(mockValue)
        .withTags(new ArrayList())
        .build();

    @Test
    void fromTimer() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final Timer mockMeter = mock(Timer.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.totalTime(TimeUnit.MILLISECONDS)).thenReturn(mockSum);
        when(mockMeter.count()).thenReturn(mockCount);
        when(mockMeter.max(TimeUnit.MILLISECONDS)).thenReturn(mockMax);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final AggregateMeasurement result = registry.fromTimer(mockMeter);

        assertEquals(expectedAggregate, result);
    }

    @Test
    void fromFunctionTimer() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final FunctionTimer mockMeter = mock(FunctionTimer.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.totalTime(TimeUnit.MILLISECONDS)).thenReturn(mockSum);
        when(mockMeter.count()).thenReturn((double) mockCount);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final AggregateMeasurement result = registry.fromFunctionTimer(mockMeter);

        assertEquals(expectedAggregateNoMax, result);
    }

    @Test
    void fromLongTaskTimer() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final LongTaskTimer mockMeter = mock(LongTaskTimer.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.duration(TimeUnit.MILLISECONDS)).thenReturn(mockSum);
        when(mockMeter.activeTasks()).thenReturn((int) mockCount);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final AggregateMeasurement result = registry.fromLongTaskTimer(mockMeter);

        assertEquals(expectedAggregateNoMax, result);
    }

    @Test
    void fromDistributionSummary() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final DistributionSummary mockMeter = mock(DistributionSummary.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.totalAmount()).thenReturn(mockSum);
        when(mockMeter.count()).thenReturn(mockCount);
        when(mockMeter.max()).thenReturn(mockMax);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final AggregateMeasurement result = registry.fromDistributionSummary(mockMeter);

        assertEquals(expectedAggregate, result);
    }

    @Test
    void fromTimeGauge() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final TimeGauge mockMeter = mock(TimeGauge.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.value(TimeUnit.MILLISECONDS)).thenReturn(mockValue);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final SingleMeasurement result = registry.fromTimeGauge(mockMeter);

        assertEquals(expectedSingle, result);
    }

    @Test
    void fromGauge() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final Gauge mockMeter = mock(Gauge.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.value()).thenReturn(mockValue);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final SingleMeasurement result = registry.fromGauge(mockMeter);

        assertEquals(expectedSingle, result);
    }

    @Test
    void fromCounter() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final Counter mockMeter = mock(Counter.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.count()).thenReturn(mockValue);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final SingleMeasurement result = registry.fromCounter(mockMeter);

        assertEquals(expectedSingle, result);
    }

    @Test
    void fromFunctionCounter() {

        final Meter.Id mockId = mock(Meter.Id.class);
        final FunctionCounter mockMeter = mock(FunctionCounter.class);

        when(mockConfig.metricPrefix()).thenReturn(mockPrefix);
        when(mockMeter.getId()).thenReturn(mockId);
        when(mockId.getName()).thenReturn(mockName);
        when(mockMeter.count()).thenReturn(mockValue);
        when(mockId.getTags()).thenReturn(new ArrayList());

        final SingleMeasurement result = registry.fromFunctionCounter(mockMeter);

        assertEquals(expectedSingle, result);
    }
}