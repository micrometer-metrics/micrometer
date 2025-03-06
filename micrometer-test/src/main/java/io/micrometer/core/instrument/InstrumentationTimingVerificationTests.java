/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(InstrumentationVerificationTests.AfterBeforeParameterResolver.class)
abstract class InstrumentationTimingVerificationTests extends InstrumentationVerificationTests {

    /**
     * A default is provided that should be preferred by new instrumentations. Existing
     * instrumentations that use a different value to maintain backwards compatibility may
     * override this method to run tests with a different name used in assertions.
     * @return name of the Timer meter produced from the timing instrumentation under test
     */
    protected abstract String timerName();

    /**
     * If an {@link ObservationDocumentation} is provided the tests run will check that
     * the produced instrumentation matches the given {@link ObservationDocumentation}.
     * @return the observation documentation to compare results against, or null to do
     * nothing
     */
    @Nullable
    protected ObservationDocumentation observationDocumentation() {
        return null;
    }

    @AfterEach
    void verifyObservationDocumentation(TestType testType) {
        ObservationDocumentation observationDocumentation = observationDocumentation();
        if (observationDocumentation == null) {
            return;
        }

        Timer timer = getRegistry().get(timerName()).timer();
        Set<String> requiredDocumentedLowCardinalityKeys = getRequiredLowCardinalityKeyNames(observationDocumentation);
        Set<String> requiredTagKeys = new HashSet<>(requiredDocumentedLowCardinalityKeys);
        if (testType == TestType.METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER) {
            requiredTagKeys.add("error");
        }
        Set<String> allDocumentedLowCardinalityKeys = getLowCardinalityKeyNames(observationDocumentation);
        Set<String> allPossibleTagKeys = new HashSet<>(allDocumentedLowCardinalityKeys);
        if (testType == TestType.METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER) {
            allPossibleTagKeys.add("error");
        }

        // must have all required tag keys
        assertThat(timer.getId().getTags()).extracting(Tag::getKey).containsAll(requiredTagKeys);
        // must not contain tag keys that aren't documented
        assertThat(timer.getId().getTags()).extracting(Tag::getKey).isSubsetOf(allPossibleTagKeys);

        if (testType == TestType.METRICS_VIA_OBSERVATIONS_WITH_METRICS_HANDLER) {
            if (observationDocumentation.getDefaultConvention() == null) {
                assertThat(getObservationRegistry()).hasObservationWithNameEqualTo(observationDocumentation.getName())
                    .that()
                    .hasContextualNameEqualTo(observationDocumentation.getContextualName());
            }
            assertThat(getObservationRegistry()).hasObservationWithNameEqualTo(timerName())
                .that()
                .hasSubsetOfKeys(getAllKeyNames(observationDocumentation));
        }
    }

    private Set<String> getRequiredLowCardinalityKeyNames(ObservationDocumentation observationDocumentation) {
        return Arrays.stream(observationDocumentation.getLowCardinalityKeyNames())
            .filter(KeyName::isRequired)
            .map(KeyName::asString)
            .collect(Collectors.toSet());
    }

    private Set<String> getLowCardinalityKeyNames(ObservationDocumentation observationDocumentation) {
        return Arrays.stream(observationDocumentation.getLowCardinalityKeyNames())
            .map(KeyName::asString)
            .collect(Collectors.toSet());
    }

    private String[] getAllKeyNames(ObservationDocumentation observationDocumentation) {
        return Stream
            .concat(Arrays.stream(observationDocumentation.getLowCardinalityKeyNames()),
                    Arrays.stream(observationDocumentation.getHighCardinalityKeyNames()))
            .map(KeyName::asString)
            .toArray(String[]::new);
    }

}
