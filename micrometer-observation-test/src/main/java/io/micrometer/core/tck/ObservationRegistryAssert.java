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

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertion methods for {@code MeterRegistry}s.
 * <p>
 * To create a new instance of this class, invoke
 * {@link ObservationRegistryAssert#assertThat(ObservationRegistry)} or
 * {@link ObservationRegistryAssert#then(ObservationRegistry)}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
@SuppressWarnings("unchecked")
public class ObservationRegistryAssert<SELF extends ObservationRegistryAssert<SELF, ACTUAL>, ACTUAL extends ObservationRegistry>
        extends AbstractAssert<SELF, ACTUAL> {

    protected ObservationRegistryAssert(ACTUAL actual) {
        super(actual, ObservationRegistryAssert.class);
    }

    protected ObservationRegistryAssert(ACTUAL actual, Class<SELF> clazz) {
        super(actual, clazz);
    }

    /**
     * Creates the assert object for {@link ObservationRegistry}.
     * @param actual observation registry to assert against
     * @return meter registry assertions
     */
    public static ObservationRegistryAssert assertThat(ObservationRegistry actual) {
        return new ObservationRegistryAssert(actual);
    }

    /**
     * Creates the assert object for {@link ObservationRegistry}.
     * @param actual observation registry to assert against
     * @return meter registry assertions
     */
    public static ObservationRegistryAssert then(ObservationRegistry actual) {
        return new ObservationRegistryAssert(actual);
    }

    /**
     * Verifies that there's no current {@link Observation} left in the
     * {@link ObservationRegistry}.
     * @return this
     * @throws AssertionError if there is a current observation remaining in the registry
     */
    public SELF doesNotHaveAnyRemainingCurrentObservation() {
        isNotNull();
        Observation current = actual.getCurrentObservation();
        if (current != null) {
            failWithMessage("Expected no current observation in the registry but found one");
        }
        return (SELF) this;
    }

    /**
     * Verifies that there's a current {@link Observation} left in the
     * {@link ObservationRegistry}.
     * @return this
     * @throws AssertionError if there is no current observation remaining in the registry
     */
    public SELF hasRemainingCurrentObservation() {
        isNotNull();
        Observation current = actual.getCurrentObservation();
        if (current == null) {
            failWithMessage("Expected an observation in the registry but found none");
        }
        return (SELF) this;
    }

    /**
     * Verifies that there's no current {@link Observation} left in the
     * {@link ObservationRegistry}.
     * @param observation to compare against
     * @return this
     * @throws AssertionError if there is a current observation remaining in the registry
     */
    public SELF doesNotHaveRemainingCurrentObservationSameAs(Observation observation) {
        isNotNull();
        Observation current = actual.getCurrentObservation();
        if (current == observation) {
            failWithMessage("Expected current observation in the registry to be different than <%s> but was the same",
                    observation);
        }
        return (SELF) this;
    }

    /**
     * Verifies that there's a current {@link Observation} left in the
     * {@link ObservationRegistry}.
     * @param observation to compare against
     * @return this
     * @throws AssertionError if there is no current observation remaining in the registry
     */
    public SELF hasRemainingCurrentObservationSameAs(Observation observation) {
        isNotNull();
        Observation current = actual.getCurrentObservation();
        if (current != observation) {
            failWithMessage("Expected current observation in the registry to be same as <%s> but was <%s>", observation,
                    current);
        }
        return (SELF) this;
    }

}
