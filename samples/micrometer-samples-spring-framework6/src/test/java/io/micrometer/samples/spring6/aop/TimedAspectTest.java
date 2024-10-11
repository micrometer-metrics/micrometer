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
package io.micrometer.samples.spring6.aop;

import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.lang.NonNull;
import io.micrometer.core.Issue;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.TimeUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(registry.getMeters()).isEmpty();
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
            .longTaskTimers()).hasSize(1);
    }

    @Test
    void timeMethodWithSloTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.sloCall();

        assertThat(registry.get("sloCall")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "sloCall")
            .tag("extra", "tag")
            .timer()
            .takeSnapshot()
            .histogramCounts()).extracting(CountAtBucket::bucket)
            .containsExactly(TimeUtils.secondsToUnit(0.1, TimeUnit.NANOSECONDS),
                    TimeUtils.secondsToUnit(0.5, TimeUnit.NANOSECONDS));
    }

    @Test
    void timeMethodWithPercentilesTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.percentilesCall();

        assertThat(registry.get("percentilesCall")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "percentilesCall")
            .timer()
            .takeSnapshot()
            .percentileValues()).extracting(ValueAtPercentile::percentile).containsExactly(0.1, 0.5);
    }

    @Test
    void timeMethodFailure() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(failingRegistry.getMeters()).isEmpty();
    }

    @Test
    void timeMethodFailureWithLongTaskTimer() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedService service = pf.getProxy();

        service.longCall();

        assertThat(failingRegistry.getMeters()).isEmpty();
    }

    @Test
    void timeMethodWithError() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        assertThat(registry.getMeters()).isEmpty();

        assertThatThrownBy(service::callRaisingError).isInstanceOf(TestError.class);

        assertThat(registry.get("callRaisingError")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "callRaisingError")
            .tag("extra", "tag")
            .tag("exception", "TestError")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeMethodWithErrorAndLongTaskTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        assertThat(registry.getMeters()).isEmpty();

        assertThatThrownBy(service::longCallRaisingError).isInstanceOf(TestError.class);

        assertThat(registry.get("longCallRaisingError")
            .tag("class", getClass().getName() + "$TimedService")
            .tag("method", "longCallRaisingError")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodWhenCompleted() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);

        assertThat(registry.getMeters()).isEmpty();

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

        assertThat(registry.getMeters()).isEmpty();

        guardedResult.complete(new IllegalStateException("simulated"));
        assertThatThrownBy(completableFuture::join).isInstanceOf(CompletionException.class);

        assertThat(registry.get("call")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "call")
            .tag("extra", "tag")
            .tag("exception", "IllegalStateException")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void timeMethodWhenReturnCompletionStageNull() {
        MeterRegistry registry = new SimpleMeterRegistry();
        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));
        AsyncTimedService service = pf.getProxy();
        CompletableFuture<?> completableFuture = service.callNull();
        assertThat(completableFuture).isNull();
        assertThat(registry.getMeters()).isNotEmpty();
        assertThat(registry.get("callNull")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "callNull")
            .tag("extra", "tag")
            .tag("exception", "none")
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

        guardedResult.complete(new IllegalStateException("simulated"));
        assertThatThrownBy(completableFuture::join).isInstanceOf(CompletionException.class);

        assertThat(registry.get("longCall")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCall")
            .tag("extra", "tag")
            .longTaskTimer()
            .activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodWithLongTaskTimerWhenReturnCompletionStageNull() {
        MeterRegistry registry = new SimpleMeterRegistry();
        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));
        AsyncTimedService service = pf.getProxy();
        CompletableFuture<?> completableFuture = service.longCallNull();
        assertThat(completableFuture).isNull();
        assertThat(registry.get("longCallNull")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCallNull")
            .tag("extra", "tag")
            .longTaskTimers()).hasSize(1);
        assertThat(registry.find("longCallNull")
            .tag("class", getClass().getName() + "$AsyncTimedService")
            .tag("method", "longCallNull")
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

        assertThat(failingRegistry.getMeters()).isEmpty();
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

        assertThat(failingRegistry.getMeters()).isEmpty();
    }

    @Test
    void timeClass() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry));

        TimedClass service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
            .tag("class", this.getClass().getName() + "$TimedClass")
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

        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void timeClassImplementingInterface() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedImpl());
        pf.addAspect(new TimedAspect(registry));

        TimedInterface service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
            .tag("class", this.getClass().getName() + "$TimedInterface")
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

        assertThat(failingRegistry.getMeters()).isEmpty();
    }

    @Issue("#5584")
    void pjpFunctionThrows() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry, (Function<ProceedingJoinPoint, Iterable<Tag>>) jp -> {
            throw new RuntimeException("test");
        }));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.get("call").tag("extra", "tag").timer().count()).isEqualTo(1);
    }

    @Test
    void ignoreClassLevelAnnotationIfMethodLevelPresent() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry));

        TimedClass service = pf.getProxy();

        service.annotatedOnMethod();

        assertThat(registry.getMeters()).hasSize(1);
        assertThat(registry.get("annotatedOnMethod")
            .tag("class", this.getClass().getName() + "$TimedClass")
            .tag("method", "annotatedOnMethod")
            .tag("extra", "tag2")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    @Issue("#2461")
    void timeMethodWithJoinPoint() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry,
                (Function<ProceedingJoinPoint, Iterable<Tag>>) jp -> Tags.of("extra", "override")));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.get("call").tag("extra", "override").timer().count()).isEqualTo(1);
    }

    @Nested
    class MeterTagsTests {

        ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

        ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

        MeterTagAnnotationHandler meterTagAnnotationHandler = new MeterTagAnnotationHandler(aClass -> valueResolver,
                aClass -> valueExpressionResolver);

        MeterRegistry registry;
        TimedAspect timedAspect;

        @BeforeEach
        void setup() {
            registry = new SimpleMeterRegistry();
            timedAspect = new TimedAspect(registry);
            timedAspect.setMeterTagAnnotationHandler(meterTagAnnotationHandler);
        }

        @ParameterizedTest
        @EnumSource
        void meterTagsWithText(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry.get("method.timed")
                .tag("test", "15")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource
        void meterTagsWithResolver(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry.get("method.timed")
                .tag("test", "Value from myCustomTagValueResolver [foo]")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource
        void meterTagsWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry.get("method.timed")
                .tag("test", "hello characters.overridden")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource
        void multipleMeterTagsWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2.overridden: qwe")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource
        void multipleMeterTagsWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsWithContainerForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2: qwe")
                .tag("value3", "value3.overridden: ZXEQWE")
                .timer()
                .count()).isEqualTo(1);
        }

        @Test
        void meterTagOnPackagePrivateMethod() {
            AspectJProxyFactory pf = new AspectJProxyFactory(new MeterTagClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(timedAspect);

            MeterTagClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry.get("method.timed")
                .tag("foo", "bar")
                .timer()
                .count()).isEqualTo(1);
        }

        @Test
        void meterTagOnSuperClass() {
            MeterTagSub service = getProxyWithTimedAspect(new MeterTagSub());

            service.superMethod("someValue");

            assertThat(registry.get("method.timed")
                .tag("superTag", "someValue")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsOnReturnValueWithText(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForArgumentToString();

            assertThat(registry.get("method.timed")
                .tag("test", "15")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsOnReturnValueWithResolver(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueResolver();

            assertThat(registry.get("method.timed")
                .tag("test", "Value from myCustomTagValueResolver [foo]")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void meterTagsOnReturnValueWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueExpression();

            assertThat(registry.get("method.timed")
                .tag("test", "hello characters. overridden")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void multipleMeterTagsOnReturnValueWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsForTagValueExpression();

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2. overridden: qwe")
                .timer()
                .count()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(AnnotatedTestClass.class)
        void multipleMeterTagsOnReturnValueWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            MeterTagClassInterface service = getProxyWithTimedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsWithContainerForTagValueExpression();

            assertThat(registry.get("method.timed")
                .tag("value1", "value1: zxe")
                .tag("value2", "value2: qwe")
                .tag("value3", "value3. overridden: ZXEQWE")
                .timer()
                .count()).isEqualTo(1);
        }

        @Test
        void meterTagOnReturnValueOnPackagePrivateMethod() {
            AspectJProxyFactory pf = new AspectJProxyFactory(new MeterTagClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(timedAspect);

            MeterTagClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod();

            assertThat(registry.get("method.timed")
                .tag("foo", "bar")
                .timer()
                .count()).isEqualTo(1);
        }

        @Test
        void meterTagOnReturnValueOnSuperClass() {
            MeterTagSub service = getProxyWithTimedAspect(new MeterTagSub());

            service.superMethod();

            assertThat(registry.get("method.timed")
                .tag("superTag", "someValue")
                .timer()
                .count()).isEqualTo(1);
        }

        private <T> T getProxyWithTimedAspect(T object) {
            AspectJProxyFactory pf = new AspectJProxyFactory(object);
            pf.addAspect(timedAspect);
            return pf.getProxy();
        }

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
        @MeterTag(key = "test", resolver = ValueResolver.class)
        String getAnnotationForTagValueResolver();

        @Timed
        void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello' + ' characters'") String test);

        @Timed
        @MeterTag(key = "test", expression = "'hello' + ' characters'")
        String getAnnotationForTagValueExpression();

        @Timed
        void getAnnotationForArgumentToString(@MeterTag("test") Long param);

        @Timed
        @MeterTag("test")
        Long getAnnotationForArgumentToString();

        @Timed
        void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                        expression = "'value2: ' + value2") DataHolder param);

        @Timed
        @MeterTag(key = "value1", expression = "'value1: ' + value1")
        @MeterTag(key = "value2", expression = "'value2: ' + value2")
        DataHolder getMultipleAnnotationsForTagValueExpression();

        @Timed
        void getMultipleAnnotationsWithContainerForTagValueExpression(@MeterTags({
                @MeterTag(key = "value1", expression = "'value1: ' + value1"),
                @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
                        expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param);

        @Timed
        @MeterTags({
            @MeterTag(key = "value1", expression = "'value1: ' + value1"),
            @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
            expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") })
        DataHolder getMultipleAnnotationsWithContainerForTagValueExpression();

    }

    static class MeterTagClass implements MeterTagClassInterface {

        @Timed
        @Override
        public void getAnnotationForTagValueResolver(
                @MeterTag(key = "test", resolver = ValueResolver.class) String test) {
        }

        @Timed
        @MeterTag(key = "test", resolver = ValueResolver.class)
        @Override
        public String getAnnotationForTagValueResolver() {
            return "foo";
        }

        @Timed
        @Override
        public void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Timed
        @MeterTag(key = "test", expression = "'hello' + ' characters. overridden'")
        @Override
        public String getAnnotationForTagValueExpression() {
            return "foo";
        }

        @Timed
        @Override
        public void getAnnotationForArgumentToString(@MeterTag("test") Long param) {
        }

        @Timed
        @MeterTag("test")
        @Override
        public Long getAnnotationForArgumentToString() {
            return 15L;
        }

        @Timed
        void getAnnotationForPackagePrivateMethod(@MeterTag("foo") String foo) {
        }

        @Timed
        @MeterTag("foo")
        String getAnnotationForPackagePrivateMethod() {
            return "bar";
        }

        @Timed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value1", expression = "'value1: ' + value1") @MeterTag(key = "value2",
                        expression = "'value2.overridden: ' + value2") DataHolder param) {

        }


        @Timed
        @MeterTag(key = "value1", expression = "'value1: ' + value1")
        @MeterTag(key = "value2", expression = "'value2. overridden: ' + value2")
        @Override
        public DataHolder getMultipleAnnotationsForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

        @Timed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@MeterTags({
                @MeterTag(key = "value1", expression = "'value1: ' + value1"),
                @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
                        expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param) {
        }

        @Timed
        @MeterTags({
            @MeterTag(key = "value1", expression = "'value1: ' + value1"),
            @MeterTag(key = "value2", expression = "'value2: ' + value2"), @MeterTag(key = "value3",
            expression = "'value3. overridden: ' + value1.toUpperCase + value2.toUpperCase") })
        @Override
        public DataHolder getMultipleAnnotationsWithContainerForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

    }

    static class MeterTagClassChild implements MeterTagClassInterface {

        @Timed
        @Override
        public void getAnnotationForTagValueResolver(String test) {
        }

        @Timed
        @Override
        public String getAnnotationForTagValueResolver() {
            return "foo";
        }

        @Timed
        @Override
        public void getAnnotationForTagValueExpression(
                @MeterTag(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Timed
        @MeterTag(key = "test", expression = "'hello' + ' characters. overridden'")
        @Override
        public String getAnnotationForTagValueExpression() {
            return "foo";
        }

        @Timed
        @Override
        public void getAnnotationForArgumentToString(Long param) {
        }

        @Timed
        @Override
        public Long getAnnotationForArgumentToString() {
            return 15L;
        }

        @Timed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @MeterTag(key = "value2", expression = "'value2.overridden: ' + value2") DataHolder param) {
        }

        @Timed
        @Override
        @MeterTag(key = "value2", expression = "'value2. overridden: ' + value2")
        public DataHolder getMultipleAnnotationsForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

        @Timed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@MeterTag(key = "value3",
                expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") DataHolder param) {
        }

        @Timed
        @MeterTag(key = "value3", expression = "'value3. overridden: ' + value1.toUpperCase + value2.toUpperCase")
        @Override
        public DataHolder getMultipleAnnotationsWithContainerForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

    }

    static class MeterTagSuper {

        @Timed
        public void superMethod(@MeterTag("superTag") String foo) {
        }

        @Timed
        @MeterTag("superTag")
        public String superMethod() {
            return "someValue";
        }

    }

    static class MeterTagSub extends MeterTagSuper {

        @Timed
        public void subMethod(@MeterTag("subTag") String foo) {
        }

        @Timed
        @MeterTag("subTag")
        public String subMethod() {
            return "someValue";
        }

    }

    private static final class FailingMeterRegistry extends SimpleMeterRegistry {

        private FailingMeterRegistry() {
            super();
        }

        @NonNull
        @Override
        protected Timer newTimer(@NonNull Id id, @NonNull DistributionStatisticConfig distributionStatisticConfig,
                @NonNull PauseDetector pauseDetector) {
            throw new RuntimeException("FailingMeterRegistry");
        }

        @NonNull
        @Override
        protected LongTaskTimer newLongTaskTimer(@Nonnull Id id,
                @Nonnull DistributionStatisticConfig distributionStatisticConfig) {
            throw new RuntimeException("FailingMeterRegistry");
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

        @Timed(value = "percentilesCall", percentiles = { 0.1, 0.5 })
        void percentilesCall() {
        }

        @Timed(value = "callRaisingError", extraTags = { "extra", "tag" })
        void callRaisingError() {
            throw new TestError();
        }

        @Timed(value = "longCallRaisingError", extraTags = { "extra", "tag" }, longTask = true)
        void longCallRaisingError() {
            throw new TestError();
        }

    }

    static class AsyncTimedService {

        @Timed(value = "call", extraTags = { "extra", "tag" })
        CompletableFuture<?> call(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Timed(value = "callNull", extraTags = { "extra", "tag" })
        CompletableFuture<?> callNull() {
            return null;
        }

        @Timed(value = "longCall", extraTags = { "extra", "tag" }, longTask = true)
        CompletableFuture<?> longCall(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Timed(value = "longCallNull", extraTags = { "extra", "tag" }, longTask = true)
        CompletableFuture<?> longCallNull() {
            return null;
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

    static class DataHolder {

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

    static class TestError extends Error {

    }

}
