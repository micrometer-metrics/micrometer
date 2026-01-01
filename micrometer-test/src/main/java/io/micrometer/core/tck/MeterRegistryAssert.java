/*
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.tck;

import io.micrometer.common.KeyValues;
import io.micrometer.test.assertions.CounterAssert;
import io.micrometer.test.assertions.GaugeAssert;
import io.micrometer.test.assertions.MeterAssert;
import io.micrometer.test.assertions.TimerAssert;
import io.micrometer.core.instrument.*;
import org.assertj.core.annotation.CheckReturnValue;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assertion methods for {@code MeterRegistry}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link MeterRegistryAssert#assertThat(MeterRegistry)} or
 * {@link MeterRegistryAssert#then(MeterRegistry)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class MeterRegistryAssert extends AbstractAssert<MeterRegistryAssert, MeterRegistry> {

    protected MeterRegistryAssert(MeterRegistry actual) {
        super(actual, MeterRegistryAssert.class);
    }

    /**
     * Creates the assert object for {@link MeterRegistry}.
     * @param actual meter registry to assert against
     * @return meter registry assertions
     */
    @CheckReturnValue
    public static MeterRegistryAssert assertThat(MeterRegistry actual) {
        return new MeterRegistryAssert(actual);
    }

    /**
     * Creates the assert object for {@link MeterRegistry}.
     * @param actual meter registry to assert against
     * @return meter registry assertions
     */
    @CheckReturnValue
    public static MeterRegistryAssert then(MeterRegistry actual) {
        return new MeterRegistryAssert(actual);
    }

    /**
     * Verifies that there are no metrics in the registry.
     * @return this
     * @throws AssertionError if there are any metrics registered
     */
    public MeterRegistryAssert hasNoMetrics() {
        isNotNull();
        List<String> metricsNames = actual.getMeters()
            .stream()
            .map(meter -> meter.getId().getName())
            .collect(Collectors.toList());
        if (!metricsNames.isEmpty()) {
            failWithMessage("Expected no metrics, but got metrics with following names <%s>",
                    String.join(",", metricsNames));
        }
        return this;
    }

    /**
     * Verifies that a meter with given name exists in the provided {@link MeterRegistry}.
     * @param meterName name of the meter
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no meter registered under given name.
     */
    public MeterRegistryAssert hasMeterWithName(String meterName) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).meter();
        if (foundMeter == null) {
            failWithMessage("Expected a meter with name <%s> but found none.\nFound following metrics %s", meterName,
                    allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a timer with given name exists in the provided {@link MeterRegistry}.
     * @param timerName name of the timer
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name.
     */
    public MeterRegistryAssert hasTimerWithName(String timerName) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).timer();
        if (foundTimer == null) {
            failWithMessage("Expected a timer with name <%s> but found none.\nFound following metrics %s", timerName,
                    allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a meter with given name does not exist in the provided
     * {@link MeterRegistry}.
     * @param meterName name of the meter that should not be present
     * @return this
     * @throws AssertionError if there is a meter registered under given name.
     */
    public MeterRegistryAssert doesNotHaveMeterWithName(String meterName) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).meter();
        if (foundMeter != null) {
            failWithMessage("Expected no meter with name <%s> but found one with tags <%s>", meterName,
                    foundMeter.getId().getTags());
        }
        return this;
    }

    /**
     * Verifies that a timer with given name does not exist in the provided
     * {@link MeterRegistry}.
     * @param timerName name of the timer that should not be present
     * @return this
     * @throws AssertionError if there is a timer registered under given name.
     */
    public MeterRegistryAssert doesNotHaveTimerWithName(String timerName) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).timer();
        if (foundTimer != null) {
            failWithMessage("Expected no timer with name <%s> but found one with tags <%s>", timerName,
                    foundTimer.getId().getTags());
        }
        return this;
    }

    /**
     * Verifies that a meter with given name and key-value tags exists in the provided
     * {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no meter registered under given name with given
     * tags.
     */
    public MeterRegistryAssert hasMeterWithNameAndTags(String meterName, Tags tags) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).tags(tags).meter();
        if (foundMeter == null) {
            failWithMessage("Expected a meter with name <%s> and tags <%s> but found none.\nFound following metrics %s",
                    meterName, tags, allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a timer with given name and key-value tags exists in the provided
     * {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name with given
     * tags.
     */
    public MeterRegistryAssert hasTimerWithNameAndTags(String timerName, Tags tags) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).tags(tags).timer();
        if (foundTimer == null) {
            failWithMessage("Expected a timer with name <%s> and tags <%s> but found none.\nFound following metrics %s",
                    timerName, tags, allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a meter with given name and key-value tags exists in the provided
     * {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no meter registered under given name with given
     * tags.
     */
    public MeterRegistryAssert hasMeterWithNameAndTags(String meterName, KeyValues tags) {
        return hasMeterWithNameAndTags(meterName, toMicrometerTags(tags));
    }

    /**
     * Verifies that a timer with given name and key-value tags exists in the provided
     * {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name with given
     * tags.
     */
    public MeterRegistryAssert hasTimerWithNameAndTags(String timerName, KeyValues tags) {
        return hasTimerWithNameAndTags(timerName, toMicrometerTags(tags));
    }

    private Tags toMicrometerTags(KeyValues tags) {
        Tag[] array = tags.stream().map(tag -> Tag.of(tag.getKey(), tag.getValue())).toArray(Tag[]::new);
        return Tags.of(array);
    }

    /**
     * Verifies that a meter with given name and key-value tags does not exist in the
     * provided {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if there is a meter registered under given name with given
     * tags.
     */
    public MeterRegistryAssert doesNotHaveMeterWithNameAndTags(String meterName, Tags tags) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).tags(tags).meter();
        if (foundMeter != null) {
            failWithMessage("Expected no meter with name <%s> and tags <%s> but found one", meterName, tags);
        }
        return this;
    }

    /**
     * Verifies that a timer with given name and key-value tags does not exist in the
     * provided {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if there is a timer registered under given name with given
     * tags.
     */
    public MeterRegistryAssert doesNotHaveTimerWithNameAndTags(String timerName, Tags tags) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).tags(tags).timer();
        if (foundTimer != null) {
            failWithMessage("Expected no timer with name <%s> and tags <%s> but found one", timerName, tags);
        }
        return this;
    }

    /**
     * Verifies that a meter with given name and key-value tags does not exist in the
     * provided {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if there is a meter registered under given name with given
     * tags.
     */
    public MeterRegistryAssert doesNotHaveMeterWithNameAndTags(String meterName, KeyValues tags) {
        return doesNotHaveMeterWithNameAndTags(meterName, toMicrometerTags(tags));
    }

    /**
     * Verifies that a timer with given name and key-value tags does not exist in the
     * provided {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if there is a timer registered under given name with given
     * tags.
     */
    public MeterRegistryAssert doesNotHaveTimerWithNameAndTags(String timerName, KeyValues tags) {
        return doesNotHaveTimerWithNameAndTags(timerName, toMicrometerTags(tags));
    }

    /**
     * Verifies that a meter with given name and tag keys exists in the provided
     * {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tagKeys tag keys
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no meter registered under given name with given
     * tag keys.
     */
    public MeterRegistryAssert hasMeterWithNameAndTagKeys(String meterName, String... tagKeys) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).tagKeys(tagKeys).meter();
        if (foundMeter == null) {
            failWithMessage(
                    "Expected a meter with name <%s> and tag keys <%s> but found none.\nFound following metrics %s",
                    meterName, String.join(",", tagKeys), allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a timer with given name and tag keys exists in the provided
     * {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tagKeys tag keys
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name with given
     * tag keys.
     */
    public MeterRegistryAssert hasTimerWithNameAndTagKeys(String timerName, String... tagKeys) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).tagKeys(tagKeys).timer();
        if (foundTimer == null) {
            failWithMessage(
                    "Expected a timer with name <%s> and tag keys <%s> but found none.\nFound following metrics %s",
                    timerName, String.join(",", tagKeys), allMetrics());
        }
        return this;
    }

    /**
     * Verifies that a meter with given name and tag keys does not exist in the provided
     * {@link MeterRegistry}.
     * @param meterName name of the meter
     * @param tagKeys tag keys
     * @return this
     * @throws AssertionError if there is a meter registered under given name with given
     * tag keys.
     */
    public MeterRegistryAssert doesNotHaveMeterWithNameAndTagKeys(String meterName, String... tagKeys) {
        isNotNull();
        Meter foundMeter = actual.find(meterName).tagKeys(tagKeys).meter();
        if (foundMeter != null) {
            failWithMessage("Expected no meter with name <%s> and tag keys <%s> but found one", meterName,
                    String.join(",", tagKeys));
        }
        return this;
    }

    /**
     * Verifies that a timer with given name and tag keys does not exist in the provided
     * {@link MeterRegistry}.
     * @param timerName name of the timer
     * @param tagKeys tag keys
     * @return this
     * @throws AssertionError if there is a timer registered under given name with given
     * tag keys.
     */
    public MeterRegistryAssert doesNotHaveTimerWithNameAndTagKeys(String timerName, String... tagKeys) {
        isNotNull();
        Timer foundTimer = actual.find(timerName).tagKeys(tagKeys).timer();
        if (foundTimer != null) {
            failWithMessage("Expected no timer with name <%s> and tag keys <%s> but found one", timerName,
                    String.join(",", tagKeys));
        }
        return this;
    }

    private String allMetrics() {
        StringBuilder stringBuilder = new StringBuilder();
        actual.forEachMeter(meter -> stringBuilder.append("\n\tMeter with name <")
            .append(meter.getId().getName())
            .append(">")
            .append(" and type <")
            .append(meter.getId().getType())
            .append(">")
            .append(" \n\t\thas the following tags <")
            .append(meter.getId().getTags())
            .append(">\n"));
        return stringBuilder.toString();
    }

    /**
     * Finds a meter by name and tags and returns a {@link MeterAssert} for further
     * assertions.
     * @param meterName name of the meter
     * @param tags tags to match
     * @return a {@link MeterAssert} for the found meter
     * @throws AssertionError if the meter is not found
     */
    @CheckReturnValue
    public MeterAssert<?> meter(String meterName, Tag... tags) {
        return this.meter(meterName, Arrays.asList(tags));
    }

    /**
     * Finds a meter by name and tags and returns a {@link MeterAssert} for further
     * assertions.
     * @param meterName name of the meter
     * @param tags tags to match
     * @return a {@link MeterAssert} for the found meter
     * @throws AssertionError if the meter is not found
     */
    @CheckReturnValue
    public MeterAssert<?> meter(String meterName, Iterable<Tag> tags) {
        hasMeterWithName(meterName);
        Meter meter = actual.find(meterName).tags(tags).meter();

        Assertions.assertThat(meter).as("Meter with name <%s> and tags <%s>", meterName, tags).isNotNull();
        return MeterAssert.assertThat(meter);
    }

    /**
     * Finds a counter by name and tags and returns a {@link CounterAssert} for further
     * assertions.
     * <p>
     * Example: <pre><code class='java'>
     * Counter.builder("my.counter")
     *     .tag("env", "prod")
     *     .register(registry);
     *
     * MeterRegistryAssert.assertThat(registry)
     *     .counter("my.counter", Tag.of("env", "prod"))
     *     .hasCount(0);
     * </code></pre>
     * @param name name of the counter
     * @param tags tags to match
     * @return a {@link CounterAssert} for the found counter
     * @throws AssertionError if the counter is not found
     * @see CounterAssert
     */
    @CheckReturnValue
    public CounterAssert counter(String name, Tag... tags) {
        MeterAssert<?> meter = meter(name, tags).hasType(Counter.class);

        return CounterAssert.assertThat((Counter) meter.actual())
            .as("Counter with name <%s> and tags <%s>", name, tags)
            .isNotNull();
    }

    /**
     * Finds a timer by name and tags and returns a {@link TimerAssert} for further
     * assertions.
     * <p>
     * Example: <pre><code class='java'>
     * Timer.builder("my.timer")
     *     .tag("env", "prod")
     *     .register(registry);
     *
     * MeterRegistryAssert.assertThat(registry)
     *     .timer("my.timer", Tag.of("env", "prod"))
     *     .hasCount(0)
     *     .totalTime()
     *     .isEqualTo(Duration.ZERO);
     * </code></pre>
     * @param name name of the timer
     * @param tags tags to match
     * @return a {@link TimerAssert} for the found timer
     * @throws AssertionError if the timer is not found
     * @see TimerAssert
     */
    @CheckReturnValue
    public TimerAssert timer(String name, Tag... tags) {
        MeterAssert<?> meter = meter(name, tags).hasType(Timer.class);
        return TimerAssert.assertThat((Timer) meter.actual())
            .as("Timer with name <%s> and tags <%s>", name, tags)
            .isNotNull();
    }

    /**
     * Finds a gauge by name and tags and returns a {@link GaugeAssert} for further
     * assertions.
     * <p>
     * Example: <pre><code class='java'>
     * Gauge.builder("my.gauge", () -&gt; 42.0)
     *     .tag("env", "prod")
     *     .register(registry);
     *
     * MeterRegistryAssert.assertThat(registry)
     *     .gauge("my.gauge", Tag.of("env", "prod"))
     *     .hasValue(42.0);
     * </code></pre>
     * @param name name of the gauge
     * @param tags tags to match
     * @return a {@link GaugeAssert} for the found gauge
     * @throws AssertionError if the gauge is not found
     * @see GaugeAssert
     */
    @CheckReturnValue
    public GaugeAssert gauge(String name, Tag... tags) {
        MeterAssert<?> meter = meter(name, tags).hasType(Gauge.class);
        return GaugeAssert.assertThat((Gauge) meter.actual())
            .as("Gauge with name <%s> and tags <%s>", name, tags)
            .isNotNull();
    }

}
