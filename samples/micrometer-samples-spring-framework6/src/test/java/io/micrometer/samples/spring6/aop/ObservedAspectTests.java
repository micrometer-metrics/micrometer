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
package io.micrometer.samples.spring6.aop;

import io.micrometer.common.KeyValues;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.annotation.ObservationKeyValue;
import io.micrometer.observation.annotation.ObservationKeyValues;
import io.micrometer.observation.aop.CardinalityType;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.aop.ObservationKeyValueAnnotationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.assertj.core.api.AbstractThrowableAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * {@link ObservedAspect} tests.
 */
class ObservedAspectTests {

    TestObservationRegistry registry = TestObservationRegistry.create();

    @Test
    void annotatedCallShouldBeObserved() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        service.call();

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.call")
            .hasContextualNameEqualTo("test#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
            .hasLowCardinalityKeyValue("method", "call")
            .doesNotHaveError();
    }

    @Test
    void annotatedCallOnAnInterfaceObserved() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new TestBean());
        pf.addAspect(new ObservedAspect(registry));
        pf.addAspect(new AspectWithParameterHandler());

        TestBeanInterface service = pf.getProxy();
        service.testMethod("bar");

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.method")
            .hasContextualNameEqualTo("foo")
            .hasHighCardinalityKeyValue("foo", "bar")
            .doesNotHaveError();
    }

    @Test
    void annotatedCallShouldBeObservedAndErrorRecorded() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        assertThatThrownBy(service::error);

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.error")
            .hasContextualNameEqualTo("ObservedService#error")
            .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
            .hasLowCardinalityKeyValue("method", "error")
            .thenError()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();
    }

    @Test
    void annotatedAsyncCallShouldBeObserved() throws ExecutionException, InterruptedException {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask("test-result");
        CompletableFuture<String> asyncResult = service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();
        fakeAsyncTask.get();

        assertThat(asyncResult.get()).isEqualTo("test-result");
        await().atMost(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasNameEqualTo("test.async")
            .hasContextualNameEqualTo("ObservedService#async")
            .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
            .hasLowCardinalityKeyValue("method", "async")
            .doesNotHaveError();
    }

    @Test
    void annotatedAsyncCallShouldBeObservedAndErrorRecorded() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        RuntimeException simulatedException = new RuntimeException("simulated");
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask(simulatedException);
        service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();

        assertThatThrownBy(fakeAsyncTask::get).isEqualTo(simulatedException);
        await().atMost(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasNameEqualTo("test.async")
            .hasContextualNameEqualTo("ObservedService#async")
            .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
            .hasLowCardinalityKeyValue("method", "async")
            .thenError()
            .isInstanceOf(CompletionException.class)
            .rootCause()
            .isEqualTo(simulatedException);
    }

    @Test
    void customObservationConventionShouldBeUsed() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry, new CustomObservationConvention()));

        ObservedService service = pf.getProxy();
        service.call();
        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.call")
            .hasContextualNameEqualTo("test#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "24")
            .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
            .hasLowCardinalityKeyValue("method", "call");
    }

    @Test
    void skipPredicateShouldTakeEffect() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        ObservedService service = pf.getProxy();
        service.call();
        assertThat(registry).doesNotHaveAnyObservation();
    }

    @Test
    void annotatedClassShouldBeObserved() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        service.call();

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "call")
            .doesNotHaveError();
    }

    @Test
    void annotatedClassShouldBeObservedAndErrorRecorded() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        assertThatThrownBy(service::error);

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "error")
            .thenError()
            .isInstanceOf(RuntimeException.class)
            .hasMessage("simulated")
            .hasNoCause();
    }

    @Test
    void annotatedAsyncClassCallShouldBeObserved() throws ExecutionException, InterruptedException {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask("test-result");
        CompletableFuture<String> asyncResult = service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();
        fakeAsyncTask.get();

        assertThat(asyncResult.get()).isEqualTo("test-result");
        await().atMost(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "async")
            .doesNotHaveError();
    }

    @Test
    void annotatedAsyncClassCallShouldBeObservedAndErrorRecorded() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        RuntimeException simulatedException = new RuntimeException("simulated");
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask(simulatedException);
        service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();

        assertThatThrownBy(fakeAsyncTask::get).isEqualTo(simulatedException);
        await().atMost(Duration.ofMillis(200))
            .untilAsserted(() -> assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "async")
            .thenError()
            .isInstanceOf(CompletionException.class)
            .rootCause()
            .isEqualTo(simulatedException);
    }

    @Test
    void customObservationConventionShouldBeUsedForClass() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry, new CustomObservationConvention()));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        service.call();
        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "24")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "call");
    }

    @Test
    void skipPredicateShouldTakeEffectForClass() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        service.call();
        assertThat(registry).doesNotHaveAnyObservation();
    }

    @Test
    void ignoreClassLevelAnnotationIfMethodLevelPresent() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        ObservedClassLevelAnnotatedService annotatedService = new ObservedClassLevelAnnotatedService();
        AspectJProxyFactory pf = new AspectJProxyFactory(annotatedService);
        pf.addAspect(new ObservedAspect(registry));

        ObservedClassLevelAnnotatedService service = pf.getProxy();
        service.annotatedOnMethod();
        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasBeenStopped()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#annotatedOnMethod");
    }

    @Test
    void annotatedAsyncClassCallWithNullShouldBeObserved() {
        registry.observationConfig().observationHandler(new ObservationTextPublisher());
        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedClassLevelAnnotatedService());
        pf.addAspect(new ObservedAspect(registry));
        ObservedClassLevelAnnotatedService service = pf.getProxy();
        CompletableFuture<String> asyncResult = service.asyncNull();
        assertThat(asyncResult).isNull();
        assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
            .hasSingleObservationThat()
            .hasNameEqualTo("test.class")
            .hasContextualNameEqualTo("test.class#call")
            .hasLowCardinalityKeyValue("abc", "123")
            .hasLowCardinalityKeyValue("test", "42")
            .hasLowCardinalityKeyValue("class", ObservedClassLevelAnnotatedService.class.getName())
            .hasLowCardinalityKeyValue("method", "asyncNull")
            .doesNotHaveError();
    }

    @Nested
    class ObservationKeyValueTests {

        ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

        ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

        ObservationKeyValueAnnotationHandler observationKeyValueAnnotationHandler = new ObservationKeyValueAnnotationHandler(
                aClass -> valueResolver, aClass -> valueExpressionResolver);

        TestObservationRegistry registry;

        ObservedAspect observedAspect;

        @BeforeEach
        void setup() {
            registry = TestObservationRegistry.create();
            observedAspect = new ObservedAspect(registry);
            observedAspect.setObservationKeyValueAnnotationHandler(observationKeyValueAnnotationHandler);
        }

        @Test
        void observationKeyValuesWithTextLowCardinality() {
            ObservationKeyValueCardinalityClass service = getProxyWithObservedAspect(
                    new ObservationKeyValueCardinalityClass());

            service.lowCardinality("value");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("test", "value")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValuesWithTextHighCardinality() {
            ObservationKeyValueCardinalityClass service = getProxyWithObservedAspect(
                    new ObservationKeyValueCardinalityClass());

            service.highCardinlity("value");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "value")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValuesWithText(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "15")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValuesWithResolver(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "Value from myCustomTagValueResolver [foo]")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValuesWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "hello characters.overridden")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleobservationKeyValuesWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: zxe")
                .hasHighCardinalityKeyValue("value2", "value2.overridden: qwe")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleobservationKeyValuesWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsWithContainerForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: zxe")
                .hasHighCardinalityKeyValue("value2", "value2: qwe")
                .hasHighCardinalityKeyValue("value3", "value3.overridden: ZXEQWE")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnPackagePrivateMethod() {
            AspectJProxyFactory pf = new AspectJProxyFactory(new ObservationKeyValueClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(observedAspect);

            ObservationKeyValueClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("foo", "bar")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnSuperClass() {
            ObservationKeyValueSub service = getProxyWithObservedAspect(new ObservationKeyValueSub());

            service.superMethod("someValue");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("superTag", "someValue")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueWhenException() {
            ObservationKeyValueExceptionClass service = getProxyWithObservedAspect(
                    new ObservationKeyValueExceptionClass());

            AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert = assertThatThrownBy(
                    service::exceptionReturnValue);
            abstractThrowableAssert.isInstanceOf(RuntimeException.class);
            abstractThrowableAssert.hasMessage("exceptionReturnValue");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "")
                .hasError();
        }

        @Test
        void observationKeyValueOnParameterWhenException() {
            ObservationKeyValueExceptionClass service = getProxyWithObservedAspect(
                    new ObservationKeyValueExceptionClass());

            AbstractThrowableAssert<?, ? extends Throwable> abstractThrowableAssert = assertThatThrownBy(
                    () -> service.exceptionParameter("value"));
            abstractThrowableAssert.isInstanceOf(RuntimeException.class);
            abstractThrowableAssert.hasMessage("exceptionParameter");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "value")
                .hasError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValueOnReturnValueWithText(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            Long value = service.getAnnotationForReturnValueToString();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", value.toString())
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValueOnReturnValueWithResolver(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            String value = service.getReturnValueAnnotationForTagValueResolver();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", String.format("Value from myCustomTagValueResolver [%s]", value))
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observationKeyValueOnReturnValueWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getReturnValueAnnotationForTagValueExpression();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "hello characters. overridden")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleobservationKeyValueOnReturnValueWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            DataHolder value = service.getMultipleAnnotationsOnReturnValueForTagValueExpression();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: " + value.value1)
                .hasHighCardinalityKeyValue("value2", "value2. overridden: " + value.value2)
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleobservationKeyValueOnReturnValueWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            ObservationKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            DataHolder value = service.getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: " + value.value1)
                .hasHighCardinalityKeyValue("value2", "value2: " + value.value2)
                .hasHighCardinalityKeyValue("value3",
                        "value3. overridden: " + value.value1.toUpperCase(Locale.ROOT)
                                + value.value2.toUpperCase(Locale.ROOT))
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueOnPackagePrivateMethod() {
            AspectJProxyFactory pf = new AspectJProxyFactory(new ObservationKeyValueClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(observedAspect);

            ObservationKeyValueClass service = pf.getProxy();

            String value = service.getReturnValueAnnotationForPackagePrivateMethod();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("foo", value)
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueOnSuperClass() {
            ObservationKeyValueSub service = getProxyWithObservedAspect(new ObservationKeyValueSub());

            String value = service.superMethod();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("superTag", value)
                .doesNotHaveError();
        }

        @Test
        void observationKeyValuesWithTextAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "15")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValuesWithResolverAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "Value from myCustomTagValueResolver [foo]")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValuesWithExpressionAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "hello characters.overridden")
                .doesNotHaveError();
        }

        @Test
        void multipleobservationKeyValuesWithExpressionAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: zxe")
                .hasHighCardinalityKeyValue("value2", "value2.overridden: qwe")
                .doesNotHaveError();
        }

        @Test
        void multipleobservationKeyValuesWithinContainerWithExpressionAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getMultipleAnnotationsWithContainerForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: zxe")
                .hasHighCardinalityKeyValue("value2", "value2: qwe")
                .hasHighCardinalityKeyValue("value3", "value3.overridden: ZXEQWE")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnPackagePrivateMethodAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("foo", "bar")
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueWithTextAsync() throws ExecutionException, InterruptedException {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            Long value = service.getAnnotationForReturnValueToString().get();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", value.toString())
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueWithResolverAsync() throws ExecutionException, InterruptedException {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            String value = service.getReturnValueAnnotationForTagValueResolver().get();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", String.format("Value from myCustomTagValueResolver [%s]", value))
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueWithExpressionAsync() {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            service.getReturnValueAnnotationForTagValueExpression();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("test", "hello characters. overridden")
                .doesNotHaveError();
        }

        @Test
        void multipleobservationKeyValueOnReturnValueWithExpressionAsync()
                throws ExecutionException, InterruptedException {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            DataHolder value = service.getMultipleAnnotationsOnReturnValueForTagValueExpression().get();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: " + value.value1)
                .hasHighCardinalityKeyValue("value2", "value2. overridden: " + value.value2)
                .doesNotHaveError();
        }

        @Test
        void multipleobservationKeyValueOnReturnValueWithinContainerWithExpressionAsync()
                throws ExecutionException, InterruptedException {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            DataHolder value = service.getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression().get();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("value1", "value1: " + value.value1)
                .hasHighCardinalityKeyValue("value2", "value2: " + value.value2)
                .hasHighCardinalityKeyValue("value3",
                        "value3. overridden: " + value.value1.toUpperCase(Locale.ROOT)
                                + value.value2.toUpperCase(Locale.ROOT))
                .doesNotHaveError();
        }

        @Test
        void observationKeyValueOnReturnValueOnPackagePrivateMethodAsync()
                throws ExecutionException, InterruptedException {
            ObservationKeyValueAsyncClass service = getProxyWithObservedAspect(new ObservationKeyValueAsyncClass());

            String value = service.getReturnValueAnnotationForPackagePrivateMethod().get();

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasHighCardinalityKeyValue("foo", value)
                .doesNotHaveError();
        }

        private <T> T getProxyWithObservedAspect(T object) {
            AspectJProxyFactory pf = new AspectJProxyFactory(object);
            pf.addAspect(observedAspect);
            return pf.getProxy();
        }

    }

    enum AnnotatedTestClass {

        CLASS_WITHOUT_INTERFACE(ObservationKeyValueClass.class),
        CLASS_WITH_INTERFACE(ObservationKeyValueClassChild.class);

        private final Class<? extends ObservationKeyValueClassInterface> clazz;

        AnnotatedTestClass(Class<? extends ObservationKeyValueClassInterface> clazz) {
            this.clazz = clazz;
        }

        @SuppressWarnings("unchecked")
        <T extends ObservationKeyValueClassInterface> T newInstance() {
            try {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    static class ObservationKeyValueAsyncClass {

        @Observed
        public CompletableFuture<Void> getAnnotationForTagValueResolver(
                @ObservationKeyValue(key = "test", resolver = ValueResolver.class) String test) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValue(key = "test", resolver = ValueResolver.class)
        public CompletableFuture<String> getReturnValueAnnotationForTagValueResolver() {
            return CompletableFuture.completedFuture("foo");
        }

        @Observed
        public CompletableFuture<Void> getAnnotationForTagValueExpression(
                @ObservationKeyValue(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValue(key = "test", expression = "'hello' + ' characters. overridden'")
        public CompletableFuture<String> getReturnValueAnnotationForTagValueExpression() {
            return CompletableFuture.completedFuture("foo");
        }

        @Observed
        public CompletableFuture<Void> getAnnotationForArgumentToString(@ObservationKeyValue("test") Long param) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValue("test")
        public CompletableFuture<Long> getAnnotationForReturnValueToString() {
            return CompletableFuture.completedFuture(15L);
        }

        @Observed
        CompletableFuture<Void> getAnnotationForPackagePrivateMethod(@ObservationKeyValue("foo") String foo) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValue("foo")
        CompletableFuture<String> getReturnValueAnnotationForPackagePrivateMethod() {
            return CompletableFuture.completedFuture("bar");
        }

        @Observed
        public CompletableFuture<Void> getMultipleAnnotationsForTagValueExpression(
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1") @ObservationKeyValue(
                        key = "value2", expression = "'value2.overridden: ' + value2") DataHolder param) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1")
        @ObservationKeyValue(key = "value2", expression = "'value2. overridden: ' + value2")
        public CompletableFuture<DataHolder> getMultipleAnnotationsOnReturnValueForTagValueExpression() {
            return CompletableFuture.completedFuture(new DataHolder("zxe", "qwe"));
        }

        @Observed
        public CompletableFuture<Void> getMultipleAnnotationsWithContainerForTagValueExpression(@ObservationKeyValues({
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"),
                @ObservationKeyValue(key = "value3",
                        expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param) {
            return CompletableFuture.completedFuture(null);
        }

        @Observed
        @ObservationKeyValues({ @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"),
                @ObservationKeyValue(key = "value3",
                        expression = "'value3. overridden: ' + value1.toUpperCase + value2.toUpperCase") })
        public CompletableFuture<DataHolder> getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression() {
            return CompletableFuture.completedFuture(new DataHolder("zxe", "qwe"));
        }

    }

    static class ObservationKeyValueExceptionClass {

        @Observed
        @ObservationKeyValue(key = "test")
        public String exceptionReturnValue() {
            throw new RuntimeException("exceptionReturnValue");
        }

        @Observed
        public String exceptionParameter(@ObservationKeyValue(key = "test") String param) {
            throw new RuntimeException("exceptionParameter");
        }

    }

    static class ObservationKeyValueCardinalityClass {

        @Observed
        void lowCardinality(@ObservationKeyValue(key = "test", cardinality = CardinalityType.LOW) String test) {
        }

        @Observed
        void highCardinlity(@ObservationKeyValue(key = "test", cardinality = CardinalityType.HIGH) String test) {
        }

    }

    interface ObservationKeyValueClassInterface {

        @Observed
        void getAnnotationForTagValueResolver(
                @ObservationKeyValue(key = "test", resolver = ValueResolver.class) String test);

        @Observed
        @ObservationKeyValue(key = "test", resolver = ValueResolver.class)
        String getReturnValueAnnotationForTagValueResolver();

        @Observed
        void getAnnotationForTagValueExpression(
                @ObservationKeyValue(key = "test", expression = "'hello' + ' characters'") String test);

        @Observed
        @ObservationKeyValue(key = "test", expression = "'hello' + ' characters'")
        String getReturnValueAnnotationForTagValueExpression();

        @Observed
        void getAnnotationForArgumentToString(@ObservationKeyValue("test") Long param);

        @Observed
        @ObservationKeyValue("test")
        Long getAnnotationForReturnValueToString();

        @Observed
        void getMultipleAnnotationsForTagValueExpression(
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1") @ObservationKeyValue(
                        key = "value2", expression = "'value2: ' + value2") DataHolder param);

        @Observed
        @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1")
        @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2")
        DataHolder getMultipleAnnotationsOnReturnValueForTagValueExpression();

        @Observed
        void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservationKeyValues({
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"),
                @ObservationKeyValue(key = "value3",
                        expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param);

        @Observed
        @ObservationKeyValues({ @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"), @ObservationKeyValue(
                        key = "value3", expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") })
        DataHolder getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression();

    }

    static class ObservationKeyValueClass implements ObservationKeyValueClassInterface {

        @Observed
        @Override
        public void getAnnotationForTagValueResolver(
                @ObservationKeyValue(key = "test", resolver = ValueResolver.class) String test) {
        }

        @Observed
        @ObservationKeyValue(key = "test", resolver = ValueResolver.class)
        @Override
        public String getReturnValueAnnotationForTagValueResolver() {
            return "foo";
        }

        @Observed
        @Override
        public void getAnnotationForTagValueExpression(
                @ObservationKeyValue(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Observed
        @ObservationKeyValue(key = "test", expression = "'hello' + ' characters. overridden'")
        @Override
        public String getReturnValueAnnotationForTagValueExpression() {
            return "foo";
        }

        @Observed
        @Override
        public void getAnnotationForArgumentToString(@ObservationKeyValue("test") Long param) {
        }

        @Observed
        @ObservationKeyValue("test")
        @Override
        public Long getAnnotationForReturnValueToString() {
            return 15L;
        }

        @Observed
        void getAnnotationForPackagePrivateMethod(@ObservationKeyValue("foo") String foo) {
        }

        @Observed
        @ObservationKeyValue("foo")
        String getReturnValueAnnotationForPackagePrivateMethod() {
            return "bar";
        }

        @Observed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1") @ObservationKeyValue(
                        key = "value2", expression = "'value2.overridden: ' + value2") DataHolder param) {

        }

        @Observed
        @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1")
        @ObservationKeyValue(key = "value2", expression = "'value2. overridden: ' + value2")
        @Override
        public DataHolder getMultipleAnnotationsOnReturnValueForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

        @Observed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservationKeyValues({
                @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"),
                @ObservationKeyValue(key = "value3",
                        expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param) {
        }

        @Observed
        @ObservationKeyValues({ @ObservationKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservationKeyValue(key = "value2", expression = "'value2: ' + value2"),
                @ObservationKeyValue(key = "value3",
                        expression = "'value3. overridden: ' + value1.toUpperCase + value2.toUpperCase") })
        @Override
        public DataHolder getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

    }

    static class ObservationKeyValueClassChild implements ObservationKeyValueClassInterface {

        @Observed
        @Override
        public void getAnnotationForTagValueResolver(String test) {
        }

        @Observed
        @Override
        public String getReturnValueAnnotationForTagValueResolver() {
            return "foo";
        }

        @Observed
        @Override
        public void getAnnotationForTagValueExpression(
                @ObservationKeyValue(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Observed
        @ObservationKeyValue(key = "test", expression = "'hello' + ' characters. overridden'")
        @Override
        public String getReturnValueAnnotationForTagValueExpression() {
            return "foo";
        }

        @Observed
        @Override
        public void getAnnotationForArgumentToString(Long param) {
        }

        @Observed
        @Override
        public Long getAnnotationForReturnValueToString() {
            return 15L;
        }

        @Observed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @ObservationKeyValue(key = "value2", expression = "'value2.overridden: ' + value2") DataHolder param) {
        }

        @Observed
        @ObservationKeyValue(key = "value2", expression = "'value2. overridden: ' + value2")
        @Override
        public DataHolder getMultipleAnnotationsOnReturnValueForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

        @Observed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservationKeyValue(key = "value3",
                expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") DataHolder param) {
        }

        @Observed
        @ObservationKeyValue(key = "value3",
                expression = "'value3. overridden: ' + value1.toUpperCase + value2.toUpperCase")
        @Override
        public DataHolder getMultipleAnnotationsOnReturnValueWithContainerForTagValueExpression() {
            return new DataHolder("zxe", "qwe");
        }

    }

    static class ObservationKeyValueSuper {

        @Observed
        public void superMethod(@ObservationKeyValue("superTag") String foo) {
        }

        @Observed
        @ObservationKeyValue("superTag")
        public String superMethod() {
            return "someValue";
        }

    }

    static class ObservationKeyValueSub extends ObservationKeyValueSuper {

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

    static class ObservedService {

        @Observed(name = "test.call", contextualName = "test#call",
                lowCardinalityKeyValues = { "abc", "123", "test", "42" })
        void call() {
            System.out.println("call");
        }

        @Observed(name = "test.error")
        void error() {
            System.out.println("error");
            throw new RuntimeException("simulated");
        }

        @Observed(name = "test.async")
        CompletableFuture<String> async(FakeAsyncTask fakeAsyncTask) {
            System.out.println("async");
            ContextSnapshot contextSnapshot = ContextSnapshotFactory.builder()
                .captureKeyPredicate(key -> true)
                .contextRegistry(ContextRegistry.getInstance())
                .build()
                .captureAll();
            return CompletableFuture.supplyAsync(fakeAsyncTask,
                    contextSnapshot.wrapExecutor(Executors.newSingleThreadExecutor()));
        }

    }

    interface TestBeanInterface {

        @Observed(name = "test.method", contextualName = "foo")
        default void testMethod(@HighCardinality(key = "foo") String foo) {

        }

    }

    // Example of an implementation class
    static class TestBean implements TestBeanInterface {

    }

    @Aspect
    static class AspectWithParameterHandler {

        private final HighCardinalityAnnotationHandler handler = new HighCardinalityAnnotationHandler(
                aClass -> parameter -> "", aClass -> (expression, parameter) -> "");

        private final ObservationRegistry observationRegistry = ObservationRegistry.create();

        @Around("execution (@io.micrometer.observation.annotation.Observed * *.*(..))")
        public @Nullable Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
            Observation observation = observationRegistry.getCurrentObservation();
            handler.addAnnotatedParameters(observation, pjp);
            return pjp.proceed();
        }

    }

    @Observed(name = "test.class", contextualName = "test.class#call",
            lowCardinalityKeyValues = { "abc", "123", "test", "42" })
    static class ObservedClassLevelAnnotatedService {

        void call() {
            System.out.println("call");
        }

        void error() {
            System.out.println("error");
            throw new RuntimeException("simulated");
        }

        CompletableFuture<String> async(FakeAsyncTask fakeAsyncTask) {
            System.out.println("async");
            ContextSnapshot contextSnapshot = ContextSnapshotFactory.builder()
                .captureKeyPredicate(key -> true)
                .contextRegistry(ContextRegistry.getInstance())
                .build()
                .captureAll();
            return CompletableFuture.supplyAsync(fakeAsyncTask,
                    contextSnapshot.wrapExecutor(Executors.newSingleThreadExecutor()));
        }

        @Observed(name = "test.class", contextualName = "test.class#annotatedOnMethod")
        void annotatedOnMethod() {
        }

        CompletableFuture<String> asyncNull() {
            return null;
        }

    }

    static class FakeAsyncTask implements Supplier<String> {

        private final @Nullable String result;

        private final @Nullable RuntimeException exception;

        private final CountDownLatch countDownLatch;

        FakeAsyncTask(String result) {
            this(result, null);
        }

        FakeAsyncTask(RuntimeException exception) {
            this(null, exception);
        }

        private FakeAsyncTask(@Nullable String result, @Nullable RuntimeException exception) {
            this.result = result;
            this.exception = exception;
            this.countDownLatch = new CountDownLatch(1);
        }

        public void proceed() {
            countDownLatch.countDown();
        }

        @Override
        public @Nullable String get() {
            try {
                countDownLatch.await();
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if (exception != null) {
                throw exception;
            }
            else {
                return result;
            }
        }

    }

    static class CustomObservationConvention implements ObservationConvention<ObservedAspect.ObservedAspectContext> {

        @Override
        public KeyValues getLowCardinalityKeyValues(ObservedAspect.ObservedAspectContext context) {
            return KeyValues.of("test", "24");
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ObservedAspect.ObservedAspectContext;
        }

    }

}
