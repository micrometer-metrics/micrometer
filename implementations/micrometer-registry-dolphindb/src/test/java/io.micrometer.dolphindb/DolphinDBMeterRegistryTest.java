package io.micrometer.dolphindb;

import io.micrometer.core.instrument.*;
import org.junit.Test;
//import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
public class DolphinDBMeterRegistryTest {

    private final DolphinDBConfig config = DolphinDBConfig.DEFAULT;
    private final MockClock clock = new MockClock();
    private final DolphinDBMeterRegistry meterRegistry = new DolphinDBMeterRegistry(config, clock);

    @Test
    public void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).hasSize(1);
    }

    @Test
    public void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    public void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.get("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge)).isEmpty();
    }

    @Test
    public void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).hasSize(1);
    }

    @Test
    public void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    public void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.time.gauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.get("my.time.gauge").timeGauge();
        assertThat(meterRegistry.writeTimeGauge(timeGauge)).isEmpty();
    }

    @Test
    public void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("my.counter", 1d, Number::doubleValue)
                .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).hasSize(1);
    }

    @Test
    public void writeFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("my.counter", Double.POSITIVE_INFINITY, Number::doubleValue)
                .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();

        counter = FunctionCounter.builder("my.counter", Double.NEGATIVE_INFINITY, Number::doubleValue)
                .register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionCounter(counter)).isEmpty();
    }

}
