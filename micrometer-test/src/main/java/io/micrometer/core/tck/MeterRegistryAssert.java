/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.tck;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Strings;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Assertion methods for {@code MeterRegistry}s.
 * <p>
 * To create a new instance of this class, invoke <code>{@link MeterRegistryAssert#assertThat(MeterRegistry)}</code> 
 * or <code>{@link MeterRegistryAssert#then(MeterRegistry)}</code>.
 *
 * @author Marcin Grzejszczak
 */
public class MeterRegistryAssert extends AbstractAssert<MeterRegistryAssert, MeterRegistry> {

    protected MeterRegistryAssert(MeterRegistry actual) {
        super(actual, MeterRegistryAssert.class);
    }
   
    /**
     * Creates the assert object for {@link MeterRegistry}.
     * 
     * @param actual meter registry to assert against
     * @return meter registry assertions
     */
    public static MeterRegistryAssert assertThat(MeterRegistry actual) { 
      return new MeterRegistryAssert(actual);
    }
    
    /**
     * Creates the assert object for {@link MeterRegistry}.
     * 
     * @param actual meter registry to assert against
     * @return meter registry assertions
     */
    public static MeterRegistryAssert then(MeterRegistry actual) { 
        return new MeterRegistryAssert(actual);
    }

    /**
     * Verifies that a timer with given name exists in the provided {@link MeterRegistry}.
     * 
     * @param timerName name of the timer
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name.
     */
    public MeterRegistryAssert hasTimerWithName(String timerName) {
        isNotNull();
        Timer findTimer = actual.find(timerName).timer();
        if (findTimer == null) {
            failWithMessage("Expected a timer with name <%s> but found none", timerName);
        }
        return this;
    }
    
    /**
     * Verifies that a timer with given name and key-value tags exists in the provided {@link MeterRegistry}.
     * 
     * @param timerName name of the timer
     * @param tags key-value pairs of tags
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name with given tags.
     */
    public MeterRegistryAssert hasTimerWithNameAndTags(String timerName, Tags tags) {
        isNotNull();
        Timer findTimer = actual.find(timerName).tags(tags).timer();
        if (findTimer == null) {
            failWithMessage("Expected a timer with name <%s> and tags <%s> but found none", timerName, tags);
        }
        return this;
    }
    
    /**
     * Verifies that a timer with given name and tag keys exists in the provided {@link MeterRegistry}.
     * 
     * @param timerName name of the timer
     * @param tagKeys tag keys
     * @return this
     * @throws AssertionError if the actual value is {@code null}.
     * @throws AssertionError if there is no timer registered under given name with given tag keys.
     */
    public MeterRegistryAssert hasTimerWithNameAndTagKeys(String timerName, String... tagKeys) {
        isNotNull();
        Timer findTimer = actual.find(timerName).tagKeys(tagKeys).timer();
        if (findTimer == null) {
            failWithMessage("Expected a timer with name <%s> and tag keys <%s> but found none", timerName, Strings.join(tagKeys).with(","));
        }
        return this;
    }

}

