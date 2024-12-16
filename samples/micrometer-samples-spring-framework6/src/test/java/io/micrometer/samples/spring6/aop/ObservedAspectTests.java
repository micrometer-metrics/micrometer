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
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
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
        @Nullable
        public Object observeMethod(ProceedingJoinPoint pjp) throws Throwable {
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

        @Nullable
        private final String result;

        @Nullable
        private final RuntimeException exception;

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
        @Nullable
        public String get() {
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
        @NonNull
        public KeyValues getLowCardinalityKeyValues(@NonNull ObservedAspect.ObservedAspectContext context) {
            return KeyValues.of("test", "24");
        }

        @Override
        public boolean supportsContext(@NonNull Observation.Context context) {
            return context instanceof ObservedAspect.ObservedAspectContext;
        }

    }

}
