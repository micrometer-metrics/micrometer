package io.micrometer.core.tck;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MeterRegistryAssertTests {

    SimpleMeterRegistry simpleMeterRegistry = new SimpleMeterRegistry();
    
    MeterRegistryAssert meterRegistryAssert = new MeterRegistryAssert(simpleMeterRegistry);
    
    @Test
    void assertionErrorThrownWhenNoTimer() {
        assertThatThrownBy(() -> meterRegistryAssert.hasTimerWithName("foo"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("Expected a timer with name <foo> but found none");
    }
    
    @Test
    void assertionErrorThrownWhenTimerPresentButWrongTagKeys() {
        Timer.start(this.simpleMeterRegistry).stop(Timer.builder("matching-metric-name").tag("notmatching-tag", "baz"));
        
        assertThatThrownBy(() -> meterRegistryAssert.hasTimerWithNameAndTagKeys("matching-metric-name", "non-existant-tag"))
        .isInstanceOf(AssertionError.class)
        .hasMessage("Expected a timer with name <matching-metric-name> and tag keys <non-existant-tag> but found none");
    }
    
    @Test
    void assertionErrorThrownWhenTimerPresentButWrongTagValue() {
        Timer.start(this.simpleMeterRegistry).stop(Timer.builder("matching-metric-name").tag("matching-tag", "not-matching-value"));
        
        assertThatThrownBy(() -> meterRegistryAssert.hasTimerWithNameAndTags("matching-metric-name", Tags.of("matching-tag", "some-value")))
        .isInstanceOf(AssertionError.class)
        .hasMessage("Expected a timer with name <matching-metric-name> and tags <[tag(matching-tag=some-value)]> but found none");
    }
    
    @Test
    void noAssertionErrorThrownWhenTimerPresent() {
        Timer.start(this.simpleMeterRegistry).stop(Timer.builder("foo"));
        
        assertThatCode(() -> meterRegistryAssert.hasTimerWithName("foo"))
        .doesNotThrowAnyException();
    }
    
    @Test
    void noAssertionErrorThrownWhenTimerWithTagKeysPresent() {
        Timer.start(this.simpleMeterRegistry).stop(Timer.builder("matching-metric-name").tag("matching-tag", "baz"));
        
        assertThatCode(() -> meterRegistryAssert.hasTimerWithNameAndTagKeys("matching-metric-name", "matching-tag"))
        .doesNotThrowAnyException();
    }
    
    @Test
    void noAssertionErrorThrownWhenTimerWithTagPresent() {
        Timer.start(this.simpleMeterRegistry).stop(Timer.builder("matching-metric-name").tag("matching-tag", "matching-value"));
        
        assertThatCode(() -> meterRegistryAssert.hasTimerWithNameAndTags("matching-metric-name", Tags.of("matching-tag", "matching-value")))
        .doesNotThrowAnyException();
    }

}
