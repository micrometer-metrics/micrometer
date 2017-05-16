package org.springframework.metrics.instrument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractMeterRegistryTest {

    @ParameterizedTest
    @ArgumentsSource(MeterRegistriesProvider.class)
    @DisplayName("meters with the same name and tags are registered once")
    void uniqueMeters(MeterRegistry registry) {
        registry.counter("foo");
        registry.counter("foo");

        assertThat(registry.getMeters().size()).isEqualTo(1);
    }
}
