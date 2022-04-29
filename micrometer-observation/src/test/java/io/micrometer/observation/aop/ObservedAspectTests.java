package io.micrometer.observation.aop;

import io.micrometer.core.tck.ObservationRegistryAssert;
import io.micrometer.core.tck.TestObservationRegistry;
import io.micrometer.core.tck.TestObservationRegistryAssert;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
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

        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("test.call")
                .hasContextualNameEqualTo("ObservedService#call")
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

        TestObservationRegistryAssert.assertThat(registry)
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("test.error")
                .hasContextualNameEqualTo("ObservedService#error")
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
