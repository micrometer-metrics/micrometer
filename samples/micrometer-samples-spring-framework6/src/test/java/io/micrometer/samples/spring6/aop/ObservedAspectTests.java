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
import io.micrometer.observation.annotation.ObservedKeyValue;
import io.micrometer.observation.annotation.ObservedKeyValues;
import io.micrometer.observation.aop.CardinalityType;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.aop.ObservedKeyValueAnnotationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.Duration;
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
    class ObservedKeyValueTests {

        ValueResolver valueResolver = parameter -> "Value from myCustomTagValueResolver [" + parameter + "]";

        ValueExpressionResolver valueExpressionResolver = new SpelValueExpressionResolver();

        ObservedKeyValueAnnotationHandler observedKeyValueAnnotationHandler = new ObservedKeyValueAnnotationHandler(
                aClass -> valueResolver, aClass -> valueExpressionResolver);

        TestObservationRegistry registry;

        ObservedAspect observedAspect;

        @BeforeEach
        void setup() {
            registry = TestObservationRegistry.create();
            observedAspect = new ObservedAspect(registry);
            observedAspect.setObservedKeyValueAnnotationHandler(observedKeyValueAnnotationHandler);
        }

        @Test
        void observedKeyValuesWithTextLowCardinality() {
            ObservedKeyValueCardinalityClass service = getProxyWithObservedAspect(
                    new ObservedKeyValueCardinalityClass());

            service.lowCardinality("value");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("test", "value")
                .doesNotHaveError();
        }

        @Test
        void observedKeyValuesWithTextHighCardinality() {
            ObservedKeyValueCardinalityClass service = getProxyWithObservedAspect(
                    new ObservedKeyValueCardinalityClass());

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
        void observedKeyValuesWithText(AnnotatedTestClass annotatedClass) {
            ObservedKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForArgumentToString(15L);

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("test", "15")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observedKeyValuesWithResolver(AnnotatedTestClass annotatedClass) {
            ObservedKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueResolver("foo");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("test", "Value from myCustomTagValueResolver [foo]")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void observedKeyValuesWithExpression(AnnotatedTestClass annotatedClass) {
            ObservedKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getAnnotationForTagValueExpression("15L");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("test", "hello characters.overridden")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleObservedKeyValuesWithExpression(AnnotatedTestClass annotatedClass) {
            ObservedKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("value1", "value1: zxe")
                .hasLowCardinalityKeyValue("value2", "value2.overridden: qwe")
                .doesNotHaveError();
        }

        @ParameterizedTest
        @EnumSource
        void multipleObservedKeyValuesWithinContainerWithExpression(AnnotatedTestClass annotatedClass) {
            ObservedKeyValueClassInterface service = getProxyWithObservedAspect(annotatedClass.newInstance());

            service.getMultipleAnnotationsWithContainerForTagValueExpression(new DataHolder("zxe", "qwe"));

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("value1", "value1: zxe")
                .hasLowCardinalityKeyValue("value2", "value2: qwe")
                .hasLowCardinalityKeyValue("value3", "value3.overridden: ZXEQWE")
                .doesNotHaveError();
        }

        @Test
        void observedKeyValueOnPackagePrivateMethod() {
            AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedKeyValueClass());
            pf.setProxyTargetClass(true);
            pf.addAspect(observedAspect);

            ObservedKeyValueClass service = pf.getProxy();

            service.getAnnotationForPackagePrivateMethod("bar");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("foo", "bar")
                .doesNotHaveError();
        }

        @Test
        void observedKeyValueOnSuperClass() {
            ObservedKeyValueSub service = getProxyWithObservedAspect(new ObservedKeyValueSub());

            service.superMethod("someValue");

            assertThat(registry).doesNotHaveAnyRemainingCurrentObservation()
                .hasSingleObservationThat()
                .hasBeenStopped()
                .hasNameEqualTo("method.observed")
                .hasLowCardinalityKeyValue("superTag", "someValue")
                .doesNotHaveError();
        }

        private <T> T getProxyWithObservedAspect(T object) {
            AspectJProxyFactory pf = new AspectJProxyFactory(object);
            pf.addAspect(observedAspect);
            return pf.getProxy();
        }

    }

    enum AnnotatedTestClass {

        CLASS_WITHOUT_INTERFACE(ObservedKeyValueClass.class), CLASS_WITH_INTERFACE(ObservedKeyValueClassChild.class);

        private final Class<? extends ObservedKeyValueClassInterface> clazz;

        AnnotatedTestClass(Class<? extends ObservedKeyValueClassInterface> clazz) {
            this.clazz = clazz;
        }

        @SuppressWarnings("unchecked")
        <T extends ObservedKeyValueClassInterface> T newInstance() {
            try {
                return (T) clazz.getDeclaredConstructor().newInstance();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

    static class ObservedKeyValueCardinalityClass {

        @Observed
        void lowCardinality(@ObservedKeyValue(key = "test", cardinality = CardinalityType.LOW) String test) {
        }

        @Observed
        void highCardinlity(@ObservedKeyValue(key = "test", cardinality = CardinalityType.HIGH) String test) {
        }

    }

    interface ObservedKeyValueClassInterface {

        @Observed
        void getAnnotationForTagValueResolver(
                @ObservedKeyValue(key = "test", resolver = ValueResolver.class) String test);

        @Observed
        void getAnnotationForTagValueExpression(
                @ObservedKeyValue(key = "test", expression = "'hello' + ' characters'") String test);

        @Observed
        void getAnnotationForArgumentToString(@ObservedKeyValue("test") Long param);

        @Observed
        void getMultipleAnnotationsForTagValueExpression(
                @ObservedKeyValue(key = "value1", expression = "'value1: ' + value1") @ObservedKeyValue(key = "value2",
                        expression = "'value2: ' + value2") DataHolder param);

        @Observed
        void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservedKeyValues({
                @ObservedKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservedKeyValue(key = "value2", expression = "'value2: ' + value2"), @ObservedKeyValue(key = "value3",
                        expression = "'value3: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param);

    }

    static class ObservedKeyValueClass implements ObservedKeyValueClassInterface {

        @Observed
        @Override
        public void getAnnotationForTagValueResolver(
                @ObservedKeyValue(key = "test", resolver = ValueResolver.class) String test) {
        }

        @Observed
        @Override
        public void getAnnotationForTagValueExpression(
                @ObservedKeyValue(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Observed
        @Override
        public void getAnnotationForArgumentToString(@ObservedKeyValue("test") Long param) {
        }

        @Observed
        void getAnnotationForPackagePrivateMethod(@ObservedKeyValue("foo") String foo) {
        }

        @Observed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @ObservedKeyValue(key = "value1", expression = "'value1: ' + value1") @ObservedKeyValue(key = "value2",
                        expression = "'value2.overridden: ' + value2") DataHolder param) {

        }

        @Observed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservedKeyValues({
                @ObservedKeyValue(key = "value1", expression = "'value1: ' + value1"),
                @ObservedKeyValue(key = "value2", expression = "'value2: ' + value2"), @ObservedKeyValue(key = "value3",
                        expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") }) DataHolder param) {
        }

    }

    static class ObservedKeyValueClassChild implements ObservedKeyValueClassInterface {

        @Observed
        @Override
        public void getAnnotationForTagValueResolver(String test) {
        }

        @Observed
        @Override
        public void getAnnotationForTagValueExpression(
                @ObservedKeyValue(key = "test", expression = "'hello' + ' characters.overridden'") String test) {
        }

        @Observed
        @Override
        public void getAnnotationForArgumentToString(Long param) {
        }

        @Observed
        @Override
        public void getMultipleAnnotationsForTagValueExpression(
                @ObservedKeyValue(key = "value2", expression = "'value2.overridden: ' + value2") DataHolder param) {
        }

        @Observed
        @Override
        public void getMultipleAnnotationsWithContainerForTagValueExpression(@ObservedKeyValue(key = "value3",
                expression = "'value3.overridden: ' + value1.toUpperCase + value2.toUpperCase") DataHolder param) {
        }

    }

    static class ObservedKeyValueSuper {

        @Observed
        public void superMethod(@ObservedKeyValue("superTag") String foo) {
        }

    }

    static class ObservedKeyValueSub extends ObservedKeyValueSuper {

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
