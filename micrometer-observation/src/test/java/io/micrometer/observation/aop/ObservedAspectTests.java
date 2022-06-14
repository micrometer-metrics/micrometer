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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.annotation.Observed;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

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
                .hasLowCardinalityKeyValue("method", "call").thenThrowable().doesNotThrowAnyException();
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
                .hasLowCardinalityKeyValue("method", "error").thenThrowable().isInstanceOf(RuntimeException.class)
                .hasMessage("simulated").hasNoCause();
    }

    @Test
    void annotatedAsyncCallShouldBeObserved() throws ExecutionException, InterruptedException {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask("test-result");
        CompletableFuture<String> asyncResult = service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();
        fakeAsyncTask.get();

        assertThat(asyncResult.get()).isEqualTo("test-result");
        await().atMost(Duration.ofMillis(200)).untilAsserted(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped()
                .hasNameEqualTo("test.async").hasContextualNameEqualTo("ObservedService#async")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "async").thenThrowable().doesNotThrowAnyException();
    }

    @Test
    void annotatedAsyncCallShouldBeObservedAndErrorRecorded() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry));

        ObservedService service = pf.getProxy();
        RuntimeException simulatedException = new RuntimeException("simulated");
        FakeAsyncTask fakeAsyncTask = new FakeAsyncTask(simulatedException);
        service.async(fakeAsyncTask);
        fakeAsyncTask.proceed();

        assertThatThrownBy(fakeAsyncTask::get).isEqualTo(simulatedException);
        await().atMost(Duration.ofMillis(200)).untilAsserted(
                () -> TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped());

        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped()
                .hasNameEqualTo("test.async").hasContextualNameEqualTo("ObservedService#async")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "async").thenThrowable().isInstanceOf(CompletionException.class)
                .rootCause().isEqualTo(simulatedException);
    }

    @Test
    void customKeyValuesProviderShouldBeUsed() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry, new CustomKeyValuesProvider()));

        ObservedService service = pf.getProxy();
        service.call();
        TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasBeenStopped()
                .hasNameEqualTo("test.call").hasContextualNameEqualTo("ObservedService#call")
                .hasLowCardinalityKeyValue("test", "42")
                .hasLowCardinalityKeyValue("class", ObservedService.class.getName())
                .hasLowCardinalityKeyValue("method", "call");
    }

    @Test
    void shouldSkipPredicateShouldTakeEffect() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTextPublisher());

        AspectJProxyFactory pf = new AspectJProxyFactory(new ObservedService());
        pf.addAspect(new ObservedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        ObservedService service = pf.getProxy();
        service.call();
        TestObservationRegistryAssert.assertThat(registry).doesNotHaveAnyObservation();
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

        @Observed("test.async")
        CompletableFuture<String> async(FakeAsyncTask fakeAsyncTask) {
            System.out.println("async");
            return CompletableFuture.supplyAsync(fakeAsyncTask);
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

    static class CustomKeyValuesProvider
            implements Observation.KeyValuesProvider<ObservedAspect.ObservedAspectContext> {

        @Override
        @NonNull
        public KeyValues getLowCardinalityKeyValues(@NonNull ObservedAspect.ObservedAspectContext context) {
            return KeyValues.of("test", "42");
        }

        @Override
        public boolean supportsContext(@NonNull Observation.Context context) {
            return context instanceof ObservedAspect.ObservedAspectContext;
        }

    }

}
