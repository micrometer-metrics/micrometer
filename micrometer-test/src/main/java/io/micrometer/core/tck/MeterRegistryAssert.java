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
import io.micrometer.core.instrument.*;
import org.assertj.core.api.AbstractAssert;

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
    public static MeterRegistryAssert assertThat(MeterRegistry actual) {
        return new MeterRegistryAssert(actual);
    }

    /**
     * Creates the assert object for {@link MeterRegistry}.
     * @param actual meter registry to assert against
     * @return meter registry assertions
     */
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

}
