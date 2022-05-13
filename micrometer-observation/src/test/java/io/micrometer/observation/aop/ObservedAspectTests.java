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

import io.micrometer.core.tck.TestObservationRegistry;
import io.micrometer.core.tck.TestObservationRegistryAssert;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.annotation.Observed;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ObservedAspect} tests.
 */
class ObservedAspectTests {

    @Test
    void annotatedCallShouldBeObserved() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        service.call();

        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped()
                .hasNameEqualTo("test.call").hasContextualNameEqualTo("ObservedService#call")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "call");
    }

    @Test
    void annotatedCallShouldBeObservedAndErrorRecorded() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        assertThatThrownBy(service::error);

        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped()
                .hasNameEqualTo("test.error").hasContextualNameEqualTo("ObservedService#error")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "error");
    }

    static class ObservedService {

        @Observed("test.call")
        void call() {
            System.out.println("call");
        }

        @Observed("test.error")
        void error() {
            System.out.println("error");
            throw new RuntimeException("simulated");
        }

    }

}
