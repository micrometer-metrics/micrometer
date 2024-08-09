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
import io.micrometer.common.lang.NonNull;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.assertj.core.api.Assertions.*;

class TimedAspectTest {

    @Test
    void timeMethod() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeMethodWithSkipPredicate() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.find("call").timer()).isNull();
    }

    @Test
    void timeMethodWithLongTaskTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.longCall();

        assertThat(registry.get("longCall")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimers()
            .size()).isEqualTo(1);
    }

    @Test
    void timeMethodWithSloTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.sloCall();

        assertThat(Arrays
            .stream(registry.get("sloCall")
                .tag("class", getClass().getName() + "$TimedService")
                .tag("method", "sloCall")
                .tag("extra", "tag")
                .timer()
                .takeSnapshot()
                .histogramCounts())
            .mapToDouble(CountAtBucket::bucket)
            .toArray()).isEqualTo(new double[] { Math.pow(10, 9) * 0.1, Math.pow(10, 9) * 0.5 });
    }

    @Test
    void timeMethodFailure() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedService service = pf.getProxy();

        service.call();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("call")
                .tag("class", getClass().getName() + "$TimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .timer();
        });
    }

    @Test
    void timeMethodFailureWithLongTaskTimer() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedService service = pf.getProxy();

        service.longCall();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("longCall")
                .tag("class", getClass().getName() + "$TimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer();
        });
    }

    @Test
    void timeMethodWhenCompleted() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);

        assertThat(registry.find("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "none")
            .timer()).isNull();

        guardedResult.complete();
        completableFuture.join();

        assertThat(registry.get("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "none")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeMethodWhenCompletedExceptionally() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);

        assertThat(registry.find("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "NullPointerException")
            .timer()).isNull();

        guardedResult.complete(new NullPointerException());
        catchThrowableOfType(completableFuture::join, CompletionException.class);

        assertThat(registry.get("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "NullPointerException")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeMethodWithLongTaskTimerWhenCompleted() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);

        assertThat(registry.find("longCall")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(1);

        guardedResult.complete();
        completableFuture.join();

        assertThat(registry.get("longCall")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodWithLongTaskTimerWhenCompletedExceptionally() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);

        assertThat(registry.find("longCall")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(1);

        guardedResult.complete(new NullPointerException());
        catchThrowableOfType(completableFuture::join, CompletionException.class);

        assertThat(registry.get("longCall")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodFailureWhenCompletedExceptionally() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> failingRegistry.get("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "none")
            .timer());
    }

    @Test
    void timeMethodFailureWithLongTaskTimerWhenCompleted() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("longCall")
                .tag("class", getClass().getName() + "$AsyncTimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer();
        });
    }

    @Test
    void timeClass() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry));

        TimedClass service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
            .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedClass")
            .tag("method", "call")
            .tag("extra", "tag")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeClassWithSkipPredicate() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        TimedClass service = pf.getProxy();

        service.call();

        assertThat(registry.find("call").timer()).isNull();
    }

    @Test
    void timeClassImplementingInterface() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedImpl());
        pf.addAspect(new TimedAspect(registry));

        TimedInterface service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
            .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedInterface")
            .tag("method", "call")
            .tag("extra", "tag")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeClassFailure() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedClass service = pf.getProxy();

        service.call();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedClass")
                .tag("method", "call")
                .tag("extra", "tag")
                .timer();
        });
    }

    @Test
    void ignoreClassLevelAnnotationIfMethodLevelPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry));

        TimedClass service = pf.getProxy();

        service.annotatedOnMethod();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> registry.get("call").timer());
        assertThat(registry.get("annotatedOnMethod")
            .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedClass")
            .tag("method", "annotatedOnMethod")
            .tag("extra", "tag2")
            .timer()
            .count()).isEqualTo(1);
    }

    static class MeterTagsTests {

        ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

        ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

        MeterTagAnnotationHandler meterTagAnnotationHandler = new MeterTagAnnotationHandler(aClass -> valueResolver,
                aClass -> valueExpressionResolver);

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithText(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(timedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry.get("method.timed").tag("test", "15").timer().count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithResolver(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(timedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry.get("method.timed")
                .tag("test", "Value from myCustomTagValueResolver [foo]")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsWithExpression(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(timedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry.get("method.timed").tag("test", "hello characters. overridden").timer().count())
                .isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void multipleMeterTagsWithExpression(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(timedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2. overridden: qwe")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void multipleMeterTagsWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(annotatedClass.newInstance());
            pf.addAspect(timedAspect);

            MeterTagClassInterface service = pf.getProxy();

            service.getMultipleAnnotationsWithContainerForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2: qwe")
                .tag("value3", "value3: ZXEQWE")
                .timer()
                .count()).isEqualTo(1);
        }

        @Test
        void meterTagOnPackagePrivateMethod() {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(new MeterTagClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(timedAspect);

            MeterTagClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry.get("method.timed").tag("foo", "bar").timer().count()).isEqualTo(1);
        }

        @Test
        void meterTagOnSuperClass() {
            MeterRegistry registry = new SimpleMeterRegistry();
            TimedAspect timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);

            AspectJProxyFactory pf = new AspectJProxyFactory(new MeterTagSub());
            pf.setProxyTargetClass(true);
            pf.addAspect(timedAspect);

            MeterTagSub service = pf.getProxy();

            service.superMethod("someValue");

            assertThat(registry.get("method.timed").tag("superTag", "someValue").timer().count()).isEqualTo(1);
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

            @Timed
            void getAnnotationForTagValueResolver(@MeterTag(key = "test", resolver = ValueResolver.class) String test);

            @Timed
            void getAnnotationForTagValueExpression(
                    @MeterTag(key = "test", expression = "'hello' + ' characters'") String test);

            @Timed
            void getAnnotationForArgumentToString(@MeterTag("test") Long param);

            @Timed
            void getMultipleAnnotationsForTagValueExpression(
                    @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                            expression = "'value2: ' + value2") DataHolder param);

            @Timed
            void getMultipleAnnotationsWithContainerForTagValueExpression(@MeterTags({
                    @MeterTag(key = "value1", expression = "'value1: ' + value1"),
                    @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
                            expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param);

        }

        static class MeterTagClass implements MeterTagClassInterface {

            @Timed
            @Override
            public void getAnnotationForTagValueResolver(
                    @MeterTag(key = "test", resolver = ValueResolver.class) String test) {
            }

            @Timed
            @Override
            public void getAnnotationForTagValueExpression(
                    @MeterTag(key = "test", expression = "'hello' + ' characters. overridden'") String test) {
            }

            @Timed
            @Override
            public void getAnnotationForArgumentToString(@MeterTag("test") Long param) {
            }

            @Timed
            void getAnnotationForPackagePrivateMethod(@MeterTag("foo") String foo) {
            }

            @Timed
            @Override
            public void getMultipleAnnotationsForTagValueExpression(
                    @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                            expression = "'value2. overridden: ' + value2") DataHolder param) {

            }

            @Timed
            @Override
            public void getMultipleAnnotationsWithContainerForTagValueExpression(@MeterTags({
                    @MeterTag(key = "value1", expression = "'value1: ' + value1"),
                    @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
                            expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param) {
            }

        }

        static class MeterTagClassChild implements MeterTagClassInterface {

            @Timed
            @Override
            public void getAnnotationForTagValueResolver(String test) {
            }

            @Timed
            @Override
            public void getAnnotationForTagValueExpression(
                    @MeterTag(key = "test", expression = "'hello' + ' characters. overridden'") String test) {
            }

            @Timed
            @Override
            public void getAnnotationForArgumentToString(Long param) {
            }

            @Timed
            @Override
            public void getMultipleAnnotationsForTagValueExpression(
                    @MeterTag(key = "value2", expression = "'value2. overridden: ' + value2") DataHolder param) {

            }

            @Timed
            @Override
            public void getMultipleAnnotationsWithContainerForTagValueExpression(DataHolder param) {

            }

        }

        static class MeterTagSuper {

            @Timed
            public void superMethod(@MeterTag("superTag") String foo) {
            }

        }

        static class MeterTagSub extends MeterTagSuper {

            @Timed
            public void subMethod(@MeterTag("subTag") String foo) {
            }

        }

    }

    private final class FailingMeterRegistry extends SimpleMeterRegistry {

        private FailingMeterRegistry() {
            super();
        }

        @NonNull
        @Override
        protected Timer newTimer(@NonNull Id id, @NonNull DistributionStatisticConfig distributionStatisticConfig,
                @NonNull PauseDetector pauseDetector) {
            throw new RuntimeException();
        }

        @NonNull
        @Override
        protected LongTaskTimer newLongTaskTimer(@Nonnull Id id,
                @Nonnull DistributionStatisticConfig distributionStatisticConfig) {
            throw new RuntimeException();
        }

    }

    static class TimedService {

        @Timed(value = "call", extraTags = { "extra", "tag" })
        void call() {
        }

        @Timed(value = "longCall", extraTags = { "extra", "tag" }, longTask = true)
        void longCall() {
        }

        @Timed(value = "sloCall", extraTags = { "extra", "tag" }, histogram = true,
                serviceLevelObjectives = { 0.1, 0.5 })
        void sloCall() {
        }

    }

    static class AsyncTimedService {

        @Timed(value = "call", extraTags = { "extra", "tag" })
        CompletableFuture<?> call(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Timed(value = "longCall", extraTags = { "extra", "tag" }, longTask = true)
        CompletableFuture<?> longCall(GuardedResult guardedResult) {
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

    @Timed(value = "call", extraTags = { "extra", "tag" })
    static class TimedClass {

        void call() {
        }

        @Timed(value = "annotatedOnMethod", extraTags = { "extra", "tag2" })
        void annotatedOnMethod() {
        }

    }

    interface TimedInterface {

        void call();

    }

    @Timed(value = "call", extraTags = { "extra", "tag" })
    static class TimedImpl implements TimedInterface {

        @Override
        public void call() {
        }

    }

    public static final class DataHolder {

        private final String value1;

        private final String value2;

        private DataHolder(String value1, String value2) {
            this.value1 = value1;
            this.value2 = value2;
        }

        public String getValue1() {
            return value1;
        }

        public String getValue2() {
            return value2;
        }

    }

}
