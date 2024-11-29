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
package io.micrometer.observation.aop;

import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;

import static io.micrometer.observation.aop.ObservedAspectObservationDocumentation.ObservedAspectLowCardinalityKeyName.CLASS_NAME;
import static io.micrometer.observation.aop.ObservedAspectObservationDocumentation.ObservedAspectLowCardinalityKeyName.METHOD_NAME;

/**
 * An {@link ObservationDocumentation} for {@link ObservedAspect}.
 *
 * @author Jonatan Ivanov
 */
enum ObservedAspectObservationDocumentation implements ObservationDocumentation {

    DEFAULT;

    static Observation of(ProceedingJoinPoint pjp, Observed observed, ObservationRegistry registry,
            @Nullable ObservationConvention<ObservedAspect.ObservedAspectContext> observationConvention) {
        String name = observed.name().isEmpty() ? "method.observed" : observed.name();
        Signature signature = pjp.getStaticPart().getSignature();
        String contextualName = observed.contextualName().isEmpty()
                ? signature.getDeclaringType().getSimpleName() + "#" + signature.getName() : observed.contextualName();

        Observation observation = Observation
            .createNotStarted(name, () -> new ObservedAspect.ObservedAspectContext(pjp), registry)
            .contextualName(contextualName)
            .lowCardinalityKeyValue(CLASS_NAME.asString(), signature.getDeclaringTypeName())
            .lowCardinalityKeyValue(METHOD_NAME.asString(), signature.getName())
            .lowCardinalityKeyValues(KeyValues.of(observed.lowCardinalityKeyValues()));

        if (observationConvention != null) {
            observation.observationConvention(observationConvention);
        }

        return observation;
    }

    @Override
    public String getName() {
        return "%s";
    }

    @Override
    public String getContextualName() {
        return "%s";
    }

    @Override
    public KeyName[] getLowCardinalityKeyNames() {
        return ObservedAspectLowCardinalityKeyName.values();
    }

    enum ObservedAspectLowCardinalityKeyName implements KeyName {

        CLASS_NAME {
            @Override
            public String asString() {
                return "class";
            }
        },

        METHOD_NAME {
            @Override
            public String asString() {
                return "method";
            }
        }

    }

}
