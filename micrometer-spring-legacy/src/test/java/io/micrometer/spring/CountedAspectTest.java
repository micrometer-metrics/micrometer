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
package io.micrometer.spring;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spring integration tests for {@link CountedAspect} aspect.
 *
 * @author Ali Dehghani
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = CountedAspectTest.TestCountedAspectConfig.class)
public class CountedAspectTest {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private CountedService countedService;

    @Autowired
    private AbstractCountedService abstractCountedService;

    @Test
    public void countedWithoutSuccessfulMetrics() {
        countedService.succeedWithoutMetrics();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter())
                .isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    public void countedWithSuccessfulMetrics() {
        String worked = countedService.succeedWithMetrics();

        Counter counter = meterRegistry.get("metric.success")
                .tag("method", "succeedWithMetrics")
                .tag("result", "success")
                .tag("exception", "none").counter();

        assertThat(worked).isEqualTo("Worked!");
        assertThat(counter.count()).isOne();
    }

    @Test
    public void countedWithFailure() {
        try {
            countedService.fail();
        } catch (Exception ignored) {
        }

        Counter counter = meterRegistry.get("metric.failing")
                .tag("method", "fail")
                .tag("exception", "RuntimeException")
                .tag("result", "failure").counter();

        assertThat(counter.count()).isOne();
    }

    @Test
    public void countedWithEmptyMetricNames() {
        countedService.emptyMetricName();
        try {
            countedService.emptyMetricNameWithException();
        } catch (Exception ignored) {
        }

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }

    @Test
    public void countedShouldWorkWhenImplementingAnInterface() {
        abstractCountedService.doCount();

        Counter counter = meterRegistry.get("metric.interface")
                .tag("method", "doCount")
                .tag("result", "success").counter();

        assertThat(counter.count()).isOne();
    }


    @Configuration
    @EnableAspectJAutoProxy
    @Import(CountedService.class)
    static class TestCountedAspectConfig {

        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public CountedAspect countedAspect(MeterRegistry meterRegistry) {
            return new CountedAspect(meterRegistry);
        }

        @Bean
        public AbstractCountedService abstractCountedService() {
            return new DefaultAbstractCountedService();
        }
    }

    @Service
    static class CountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        void succeedWithoutMetrics() {

        }

        @Counted(value = "metric.success")
        String succeedWithMetrics() {
            return "Worked!";
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

    interface AbstractCountedService {

        void doCount();
    }

    static class DefaultAbstractCountedService implements AbstractCountedService {

        @Override
        @Counted(value = "metric.interface")
        public void doCount() {

        }
    }
}
