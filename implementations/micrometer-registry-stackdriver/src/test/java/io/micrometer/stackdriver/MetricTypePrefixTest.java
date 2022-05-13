package io.micrometer.stackdriver;

import com.google.monitoring.v3.TimeSeries;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricTypePrefixTest {

    private final Map<String, String> config = new HashMap<>(
            Collections.singletonMap("stackdriver.projectId", "projectId"));

    private final StackdriverMeterRegistry meterRegistry = new StackdriverMeterRegistry(config::get, new MockClock());

    @Test
    void metricTypePrefixWhenNotConfigured() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        List<TimeSeries> timeSeries = meterRegistry
                .createCounter(batch, Counter.builder("counter").register(meterRegistry)).collect(Collectors.toList());
        assertThat(timeSeries).hasSize(1);
        assertThat(timeSeries.get(0).getMetric().getType()).isEqualTo("custom.googleapis.com/counter");
    }

    @Test
    void metricTypePrefixWhenConfigured() {
        config.put("stackdriver.metricTypePrefix", "external.googleapis.com/user/");

        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        List<TimeSeries> timeSeries = meterRegistry
                .createCounter(batch, Counter.builder("counter").register(meterRegistry)).collect(Collectors.toList());
        assertThat(timeSeries).hasSize(1);
        assertThat(timeSeries.get(0).getMetric().getType()).isEqualTo("external.googleapis.com/user/counter");
    }

}
