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
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CountedAspect} aspect.
 *
 * @author Ali Dehghani
 */
class CountedAspectTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CountedService countedService = getAdvisedService();

    @Test
    void countedWithoutSuccessfulMetrics() {
        countedService.succeedWithoutMetrics();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter())
                .isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void countedWithSuccessfulMetrics() {
        countedService.succeedWithMetrics();

        Counter counter = meterRegistry.get("metric.success")
                .tag("method", "succeedWithMetrics")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedService")
                .tag("extra", "tag")
                .tag("result", "success").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isNull();
    }

    @Test
    void countedWithFailure() {
        try {
            countedService.fail();
        } catch (Exception ignored) {
        }

        Counter counter = meterRegistry.get("metric.failing")
                .tag("method", "fail")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedService")
                .tag("exception", "RuntimeException")
                .tag("result", "failure").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isEqualTo("To record something");
    }

    @Test
    void countedWithEmptyMetricNames() {
        countedService.emptyMetricName();
        try {
            countedService.emptyMetricNameWithException();
        } catch (Exception ignored) {
        }

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }


    static class CountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        void succeedWithoutMetrics() {

        }

        @Counted(value = "metric.success", extraTags = {"extra", "tag"})
        void succeedWithMetrics() {

        }

        @Counted(value = "metric.failing", description = "To record something")
        void fail() {
            throw new RuntimeException("Failing always");
        }

        @Counted
        void emptyMetricName() {

        }

        @Counted
        void emptyMetricNameWithException() {
            throw new RuntimeException("This is it");
        }


    }

    private CountedService getAdvisedService() {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new CountedService());
        proxyFactory.addAspect(new CountedAspect(meterRegistry));

        return proxyFactory.getProxy();
    }

}
