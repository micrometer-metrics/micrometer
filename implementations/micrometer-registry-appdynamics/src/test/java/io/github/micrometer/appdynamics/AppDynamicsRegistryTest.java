package io.github.micrometer.appdynamics;

import com.appdynamics.agent.api.MetricPublisher;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AppDynamicsRegistryTest {

    private MockClock clock;

    private MetricPublisher publisher;

    private AppDynamicsConfig config;

    private AppDynamicsRegistry registry;

    @BeforeEach
    void init() {
        clock = new MockClock();
        config = AppDynamicsConfig.DEFAULT;
        publisher = mock(MetricPublisher.class);
        registry = new AppDynamicsRegistry(config, publisher, clock);
    }

    @Test
    void shouldPublishSumValue() {
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
                reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.counter("counter").increment(10d);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|counter, 10, SUM, SUM, COLLECTIVE]", Arrays.toString(reference.get()));
    }

    @Test
    void shouldPublishAverageValue() {
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
            reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.timer("timer").record(100, TimeUnit.MILLISECONDS);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|timer, 100, 1, 100, 100, AVERAGE, AVERAGE, INDIVIDUAL]",
                Arrays.toString(reference.get()));
    }

    @Test
    void shouldPublishObservationValue() {
        final AtomicReference<Object[]> reference = new AtomicReference<>();
        // @formatter:off
        doAnswer(invocation ->
            reference.getAndSet(invocation.getArguments())
        ).when(publisher).reportMetric(anyString(), anyLong(), anyString(), anyString(), anyString());
        // @formatter:on

        registry.gauge("gauge", 10);
        clock.add(config.step());

        registry.publish();

        assertEquals("[" + config.prefix() + "|gauge, 10, OBSERVATION, CURRENT, COLLECTIVE]",
                Arrays.toString(reference.get()));
    }

}
