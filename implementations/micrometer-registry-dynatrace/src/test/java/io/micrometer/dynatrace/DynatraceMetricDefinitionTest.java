package io.micrometer.dynatrace;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DynatraceMetricDefinitionTest {

    @Test
    void usesMetricIdAsDescriptionWhenDescriptionIsNotAvailable() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", null, null, null);

        assertThat(metric.asJson()).isEqualTo("{\"displayName\":\"custom:test.metric\"}");
    }

    @Test
    void escapesStringsInDescription() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition(
            "custom:test.metric",
            "The /\"recent cpu usage\" for the Java Virtual Machine process",
            null, null);

        assertThat(metric.asJson()).isEqualTo("{\"displayName\":\"The \\/\\\"recent cpu usage\\\" for the Java Virtual Machine process\"}");
    }

    @Test
    void addsUnitWhenAvailable() {
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", "my test metric", DynatraceMetricDefinition.DynatraceUnit.Count, null);

        assertThat(metric.asJson()).isEqualTo("{\"displayName\":\"my test metric\",\"unit\":\"Count\"}");
    }

    @Test
    void addsDimensionsWhenAvailable() {
        final Set<String> dimensions = new HashSet<>();
        dimensions.add("first");
        dimensions.add("second");
        dimensions.add("unknown");
        final DynatraceMetricDefinition metric = new DynatraceMetricDefinition("custom:test.metric", "my test metric", null, dimensions);

        assertThat(metric.asJson()).isEqualTo("{\"displayName\":\"my test metric\",\"dimensions\":[\"first\",\"second\",\"unknown\"]}");
    }
}