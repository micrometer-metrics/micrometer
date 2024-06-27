/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.aop;

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link CountedAspect} aspect.
 *
 * @author Ali Dehghani
 * @author Tommy Ludwig
 * @author Johnny Lim
 * @author Yanming Zhou
 */
class CountedAspectTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final CountedService countedService = getAdvisedService(new CountedService());

    private final AsyncCountedService asyncCountedService = getAdvisedService(new AsyncCountedService());

    @Test
    void countedWithoutSuccessfulMetrics() {
        countedService.succeedWithoutMetrics();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter()).isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void countedWithSuccessfulMetrics() {
        countedService.succeedWithMetrics();

        Counter counter = meterRegistry.get("metric.success")
            .tag("method", "succeedWithMetrics")
            .tag("class", getClass().getName() + "$CountedService")
            .tag("extra", "tag")
            .tag("result", "success")
            .counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isNull();
    }

    @Test
    void countedWithSkipPredicate() {
        CountedService countedService = getAdvisedService(new CountedService(),
                new CountedAspect(meterRegistry, (Predicate<ProceedingJoinPoint>) proceedingJoinPoint -> true));

        countedService.succeedWithMetrics();

        assertThat(meterRegistry.find("metric.success").counter()).isNull();
    }

    @Test
    void countedWithFailure() {
        try {
            countedService.fail();
        }
        catch (Exception ignored) {
        }

        Counter counter = meterRegistry.get("metric.failing")
            .tag("method", "fail")
            .tag("class", getClass().getName() + "$CountedService")
            .tag("exception", "RuntimeException")
            .tag("result", "failure")
            .counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isEqualTo("To record something");
    }

    @Test
    void countedWithEmptyMetricNames() {
        countedService.emptyMetricName();
        try {
            countedService.emptyMetricNameWithException();
        }
        catch (Exception ignored) {
        }

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }

    @Test
    void countedWithoutSuccessfulMetricsWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.succeedWithoutMetrics(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter()).isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void countedWithSuccessfulMetricsWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.succeedWithMetrics(guardedResult);

        assertThat(meterRegistry.find("metric.success")
            .tag("method", "succeedWithMetrics")
            .tag("class", getClass().getName() + "$AsyncCountedService")
            .tag("extra", "tag")
            .tag("exception", "none")
            .tag("result", "success")
            .counter()).isNull();

        guardedResult.complete();
        completableFuture.join();

        Counter counterAfterCompletion = meterRegistry.get("metric.success")
            .tag("method", "succeedWithMetrics")
            .tag("class", getClass().getName() + "$AsyncCountedService")
            .tag("extra", "tag")
            .tag("exception", "none")
            .tag("result", "success")
            .counter();

        assertThat(counterAfterCompletion.count()).isOne();
        assertThat(counterAfterCompletion.getId().getDescription()).isNull();
    }

    @Test
    void countedWithFailureWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.fail(guardedResult);

        assertThat(meterRegistry.find("metric.failing")
            .tag("method", "fail")
            .tag("class", getClass().getName() + "$AsyncCountedService")
            .tag("exception", "RuntimeException")
            .tag("result", "failure")
            .counter()).isNull();

        guardedResult.complete(new RuntimeException());
        assertThatThrownBy(completableFuture::join).isInstanceOf(RuntimeException.class);

        Counter counter = meterRegistry.get("metric.failing")
            .tag("method", "fail")
            .tag("class", getClass().getName() + "$AsyncCountedService")
            .tag("exception", "RuntimeException")
            .tag("result", "failure")
            .counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isEqualTo("To record something");
    }

    @Test
    void countedWithEmptyMetricNamesWhenCompleted() {
        GuardedResult emptyMetricNameResult = new GuardedResult();
        GuardedResult emptyMetricNameWithExceptionResult = new GuardedResult();
        CompletableFuture<?> emptyMetricNameFuture = asyncCountedService.emptyMetricName(emptyMetricNameResult);
        CompletableFuture<?> emptyMetricNameWithExceptionFuture = asyncCountedService
            .emptyMetricName(emptyMetricNameWithExceptionResult);

        assertThat(meterRegistry.find("method.counted").counters()).hasSize(0);

        emptyMetricNameResult.complete();
        emptyMetricNameWithExceptionResult.complete(new RuntimeException());
        emptyMetricNameFuture.join();
        assertThatThrownBy(emptyMetricNameWithExceptionFuture::join).isInstanceOf(RuntimeException.class);

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }

    static class CountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        void succeedWithoutMetrics() {

        }

        @Counted(value = "metric.success", extraTags = { "extra", "tag" })
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

    private <T> T getAdvisedService(T countedService) {
        return getAdvisedService(countedService, new CountedAspect(meterRegistry));
    }

    private <T> T getAdvisedService(T countedService, CountedAspect countedAspect) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(countedService);
        proxyFactory.addAspect(countedAspect);
        return proxyFactory.getProxy();
    }

    static class AsyncCountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        CompletableFuture<?> succeedWithoutMetrics(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted(value = "metric.success", extraTags = { "extra", "tag" })
        CompletableFuture<?> succeedWithMetrics(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted(value = "metric.failing", description = "To record something")
        CompletableFuture<?> fail(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted
        CompletableFuture<?> emptyMetricName(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

    }

    static class GuardedResult {

        private boolean complete;

        private RuntimeException withException;

        synchronized Object get() {
            while (!complete) {
                try {
                    wait();
                }
                catch (InterruptedException e) {
                    // Intentionally empty
                }
            }

            if (withException == null) {
                return new Object();
            }

            throw withException;
        }

        synchronized void complete() {
            complete(null);
        }

        synchronized void complete(RuntimeException withException) {
            this.complete = true;
            this.withException = withException;
            notifyAll();
        }

    }

    @Test
    void countClassWithSuccess() {
        CountedClassService service = getAdvisedService(new CountedClassService());

        service.hello();

        assertThat(meterRegistry.get("class.counted")
            .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedClassService")
            .tag("method", "hello")
            .tag("result", "success")
            .tag("exception", "none")
            .counter()
            .count()).isEqualTo(1);
    }

    @Test
    void countClassWithFailure() {
        CountedClassService service = getAdvisedService(new CountedClassService());

        assertThatThrownBy(() -> service.fail()).isInstanceOf(RuntimeException.class);

        meterRegistry.forEachMeter((m) -> {
            System.out.println(m.getId().getTags());
        });

        assertThat(meterRegistry.get("class.counted")
            .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedClassService")
            .tag("method", "fail")
            .tag("result", "failure")
            .tag("exception", "RuntimeException")
            .counter()
            .count()).isEqualTo(1);
    }

    @Test
    void ignoreClassLevelAnnotationIfMethodLevelPresent() {
        CountedClassService service = getAdvisedService(new CountedClassService());

        service.greet();

        assertThatExceptionOfType(MeterNotFoundException.class)
            .isThrownBy(() -> meterRegistry.get("class.counted").counter());

        assertThat(meterRegistry.get("method.counted")
            .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedClassService")
            .tag("method", "greet")
            .tag("result", "success")
            .tag("exception", "none")
            .counter()
            .count()).isEqualTo(1);
    }

    @Counted("class.counted")
    static class CountedClassService {

        String hello() {
            return "hello";
        }

        void fail() {
            throw new RuntimeException("Oops");
        }

        @Counted("method.counted")
        String greet() {
            return "hello";
        }

    }

    static class MeterTagsTests {

        ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

        ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

        CountedMeterTagAnnotationHandler meterTagAnnotationHandler = new CountedMeterTagAnnotationHandler(
                aClass -> valueResolver, aClass -> valueExpressionResolver);

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithText(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            CountedAspect countedAspect = new CountedAspect(registry);
            countedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(countedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry.get("method.counted").tag("test", "15").counter().count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithResolver(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            CountedAspect countedAspect = new CountedAspect(registry);
            countedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(countedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry.get("method.counted")
                .tag("test", "Value from myCustomTagValueResolver [foo]")
                .counter()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithExpression(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            CountedAspect countedAspect = new CountedAspect(registry);
            countedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(countedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry.get("method.counted").tag("test", "hello characters").counter().count()).isEqualTo(1);
        }

        @Test
        void meterTagOnPackagePrivateMethod() {
            MeterRegistry registry = new SimpleMeterRegistry();
            CountedAspect countedAspect = new CountedAspect(registry);
            countedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(new MeterTagClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(countedAspect);

            MeterTagClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry.get("method.counted").tag("foo", "bar").counter().count()).isEqualTo(1);
        }

        enum AnnotatedTestClass {

            CLASS_WITHOUT_INTERFACE(MeterTagClass.class), CLASS_WITH_INTERFACE(MeterTagClassChild.class);

            private final Class<? extends MeterTagClassInterface> clazz;

            AnnotatedTestClass(Class<? extends MeterTagClassInterface> clazz) {
                this.clazz = clazz;
            }

            @SuppressWarnings("unchecked")
            <T extends MeterTagClassInterface> T newInstance() {
                try {
                    return (T) clazz.getDeclaredConstructor().newInstance();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

        }

        interface MeterTagClassInterface {

            @Counted
            void getAnnotationForTagValueResolver(@MeterTag(key = "test", resolver = ValueResolver.class) String test);

            @Counted
            void getAnnotationForTagValueExpression(
                    @MeterTag(key = "test", expression = "'hello' + ' characters'") String test);

            @Counted
            void getAnnotationForArgumentToString(@MeterTag("test") Long param);

        }

        static class MeterTagClass implements MeterTagClassInterface {

            @Counted
            @Override
            public void getAnnotationForTagValueResolver(
                    @MeterTag(key = "test", resolver = ValueResolver.class) String test) {
            }

            @Counted
            @Override
            public void getAnnotationForTagValueExpression(
                    @MeterTag(key = "test", expression = "'hello' + ' characters'") String test) {
            }

            @Counted
            @Override
            public void getAnnotationForArgumentToString(@MeterTag("test") Long param) {
            }

            @Counted
            void getAnnotationForPackagePrivateMethod(@MeterTag("foo") String foo) {
            }

        }

        static class MeterTagClassChild implements MeterTagClassInterface {

            @Counted
            @Override
            public void getAnnotationForTagValueResolver(String test) {
            }

            @Counted
            @Override
            public void getAnnotationForTagValueExpression(String test) {
            }

            @Counted
            @Override
            public void getAnnotationForArgumentToString(Long param) {
            }

        }

    }

}
