/*
 * Copyright 2017 VMware, Inc.
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

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HistogramGaugesTest {

    @Test
    void snapshotRollsOverAfterEveryPublish() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Timer timer = Timer.builder("my.timer").serviceLevelObjectives(Duration.ofMillis(1)).register(registry);

        HistogramGauges.registerWithCommonFormat(timer, registry);

        timer.record(1, TimeUnit.MILLISECONDS);

        assertThat(registry.get("my.timer.histogram").gauge().value()).isEqualTo(1);
        timer.record(1, TimeUnit.MILLISECONDS);
        assertThat(registry.get("my.timer.histogram").gauge().value()).isEqualTo(2);
    }

    @Test
    void meterFiltersAreOnlyAppliedOnceToHistogramsAndPercentiles() {
        MeterRegistry registry = new SimpleMeterRegistry();

        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.withName("MYPREFIX." + id.getName());
            }
        });

        Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofMillis(1))
            .publishPercentiles(0.95)
            .register(registry);

        registry.get("MYPREFIX.my.timer.percentile").tag("phi", "0.95").gauge();
        registry.get("MYPREFIX.my.timer.histogram").tag("le", "0.001").gauge();
    }

    @Test
    void histogramsContainLongMaxValue() {
        MeterRegistry registry = new SimpleMeterRegistry();

        Timer timer = Timer.builder("my.timer")
            .serviceLevelObjectives(Duration.ofNanos(Long.MAX_VALUE))
            .register(registry);

        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution")
            .serviceLevelObjectives(Double.POSITIVE_INFINITY)
            .register(registry);

        HistogramGauges distributionGauges = HistogramGauges.registerWithCommonFormat(distributionSummary, registry);

        HistogramGauges timerGauges = HistogramGauges.registerWithCommonFormat(timer, registry);

        assertThat(registry.get("my.distribution.histogram").tag("le", "+Inf").gauge()).isNotNull();
        assertThat(registry.get("my.timer.histogram").tag("le", "+Inf").gauge()).isNotNull();
    }

    @Test
    void changingSnapshotShapeKeepsGaugesAssociatedWithRegisteredPercentiles() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HistogramSupport meter = mock(HistogramSupport.class);
        Meter.Id meterId = new Meter.Id("my.timer", Tags.empty(), null, null, Meter.Type.LONG_TASK_TIMER);
        HistogramSnapshot initialSnapshot = snapshot(30, new ValueAtPercentile(0.5, 10),
                new ValueAtPercentile(0.75, 20), new ValueAtPercentile(0.99, 30));
        HistogramSnapshot shortenedSnapshot = snapshot(100, new ValueAtPercentile(0.5, 50),
                new ValueAtPercentile(0.99, 99));
        when(meter.getId()).thenReturn(meterId);
        when(meter.takeSnapshot()).thenReturn(initialSnapshot, shortenedSnapshot);

        HistogramGauges.register(meter, registry, percentile -> "my.timer.percentile",
                percentile -> Tags.of("phi", Double.toString(percentile.percentile())), ValueAtPercentile::value,
                bucket -> "my.timer.histogram", bucket -> Tags.of("le", Double.toString(bucket.bucket())));

        assertThat(registry.get("my.timer.percentile").tag("phi", "0.5").gauge().value()).isEqualTo(50);
        assertThat(registry.get("my.timer.percentile").tag("phi", "0.75").gauge().value()).isEqualTo(100);
        assertThat(registry.get("my.timer.percentile").tag("phi", "0.99").gauge().value()).isEqualTo(99);
        verify(meter, times(2)).takeSnapshot();
    }

    @Test
    void changingSnapshotShapeKeepsGaugesAssociatedWithRegisteredBuckets() {
        MeterRegistry registry = new SimpleMeterRegistry();
        HistogramSupport meter = mock(HistogramSupport.class);
        Meter.Id meterId = new Meter.Id("my.timer", Tags.empty(), null, null, Meter.Type.LONG_TASK_TIMER);
        HistogramSnapshot initialSnapshot = snapshot(new CountAtBucket(10.0, 1), new CountAtBucket(20.0, 2),
                new CountAtBucket(Double.POSITIVE_INFINITY, 3));
        HistogramSnapshot shortenedSnapshot = snapshot(new CountAtBucket(10.0, 4),
                new CountAtBucket(Double.POSITIVE_INFINITY, 6));
        when(meter.getId()).thenReturn(meterId);
        when(meter.takeSnapshot()).thenReturn(initialSnapshot, shortenedSnapshot);

        HistogramGauges.register(meter, registry, percentile -> "my.timer.percentile",
                percentile -> Tags.of("phi", Double.toString(percentile.percentile())), ValueAtPercentile::value,
                bucket -> "my.timer.histogram", bucket -> Tags.of("le", Double.toString(bucket.bucket())));

        assertThat(registry.get("my.timer.histogram").tag("le", "10.0").gauge().value()).isEqualTo(4);
        assertThat(registry.get("my.timer.histogram").tag("le", "20.0").gauge().value()).isNaN();
        assertThat(registry.get("my.timer.histogram").tag("le", "Infinity").gauge().value()).isEqualTo(6);
        verify(meter, times(2)).takeSnapshot();
    }

    @Test
    void concurrentPollingUsesOneCoherentSnapshotGeneration() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        HistogramSupport meter = mock(HistogramSupport.class);
        Meter.Id meterId = new Meter.Id("my.timer", Tags.empty(), null, null, Meter.Type.LONG_TASK_TIMER);
        AtomicInteger snapshotSequence = new AtomicInteger();
        when(meter.getId()).thenReturn(meterId);
        when(meter.takeSnapshot()).thenAnswer(invocation -> percentileSnapshot(snapshotSequence.incrementAndGet()));

        HistogramGauges.register(meter, registry, percentile -> "my.timer.percentile",
                percentile -> Tags.of("phi", Double.toString(percentile.percentile())), ValueAtPercentile::value,
                bucket -> "my.timer.histogram", bucket -> Tags.of("le", Double.toString(bucket.bucket())));

        Collection<Gauge> gauges = registry.find("my.timer.percentile").gauges();
        ExecutorService executor = Executors.newFixedThreadPool(gauges.size());
        CountDownLatch ready = new CountDownLatch(gauges.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Double>> futures = gauges.stream().map(gauge -> executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return gauge.value();
            })).collect(Collectors.toList());
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Double> observedGenerations = new HashSet<>();
            for (Future<Double> future : futures) {
                observedGenerations.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(observedGenerations).hasSize(1);
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static HistogramSnapshot percentileSnapshot(double value) {
        return snapshot(value, new ValueAtPercentile(0.5, value), new ValueAtPercentile(0.75, value),
                new ValueAtPercentile(0.95, value), new ValueAtPercentile(0.99, value),
                new ValueAtPercentile(0.999, value));
    }

    private static HistogramSnapshot snapshot(double max, ValueAtPercentile... percentiles) {
        return new HistogramSnapshot(1, max, max, percentiles, null, (printStream, scaling) -> {
        });
    }

    private static HistogramSnapshot snapshot(CountAtBucket... buckets) {
        return new HistogramSnapshot(1, 0, 0, null, buckets, (printStream, scaling) -> {
        });
    }

}
