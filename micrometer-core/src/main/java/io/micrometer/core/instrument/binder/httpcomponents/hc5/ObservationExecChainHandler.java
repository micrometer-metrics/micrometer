/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents.hc5;

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicMarkableReference;

/**
 * Instruments the Apache HTTP Client 5 for both classic and async variants using the
 * {@link ExecChainHandler} and {@link AsyncExecChainHandler} contracts. This
 * instrumentation accepts {@link ApacheHttpClientObservationConvention custom convetion
 * implementations} and will use {@link DefaultApacheHttpClientObservationConvention the
 * default one} if none was provided.
 * <p>
 * For a "classic" client, it can be configured as an interceptor in the chain: <pre>
 * CloseableHttpClient client = HttpClientBuilder.create()
 *   .addExecInterceptorLast("micrometer", new ObservationExecChainHandler(observationRegistry))
 *   .build()
 * </pre>
 * <p>
 * For an "async" client, it can also be configured as an interceptor in the chain: <pre>
 * CloseableHttpAsyncClient client = HttpAsyncClients.custom()
 *   .addExecInterceptorLast("micrometer", new ObservationExecChainHandler(observationRegistry))
 *   .build();
 * </pre>
 * <p>
 * Note that the {@link ObservationExecChainHandler} must always be inserted after the
 * {@link ChainElement#RETRY retry interceptor}, preferably last.
 *
 * @author Brian Clozel
 * @since 1.12.0
 */
public class ObservationExecChainHandler implements ExecChainHandler, AsyncExecChainHandler {

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ApacheHttpClientObservationConvention observationConvention;

    public ObservationExecChainHandler(ObservationRegistry observationRegistry,
            @Nullable ApacheHttpClientObservationConvention observationConvention) {
        this.observationRegistry = observationRegistry;
        this.observationConvention = observationConvention;
    }

    public ObservationExecChainHandler(ObservationRegistry observationRegistry) {
        this(observationRegistry, null);
    }

    @Override
    public void execute(HttpRequest request, AsyncEntityProducer entityProducer, AsyncExecChain.Scope scope,
            AsyncExecChain chain, AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
        ApacheHttpClientContext observationContext = new ApacheHttpClientContext(request, scope.clientContext);
        Observation observation = ApacheHttpClientObservationDocumentation.DEFAULT
            .observation(observationConvention, DefaultApacheHttpClientObservationConvention.INSTANCE,
                    () -> observationContext, observationRegistry)
            .start();
        ObservableCancellableDependency cancellable = new ObservableCancellableDependency(observation);
        chain.proceed(request, entityProducer, cancellable.wrapScope(scope), new AsyncExecCallback() {

            @Override
            public AsyncDataConsumer handleResponse(HttpResponse response, EntityDetails entityDetails)
                    throws HttpException, IOException {
                observationContext.setResponse(response);
                observation.stop();
                return asyncExecCallback.handleResponse(response, entityDetails);
            }

            @Override
            public void handleInformationResponse(HttpResponse response) throws HttpException, IOException {
                asyncExecCallback.handleInformationResponse(response);
            }

            @Override
            public void completed() {
                asyncExecCallback.completed();
            }

            @Override
            public void failed(Exception cause) {
                observation.error(cause);
                observation.stop();
                asyncExecCallback.failed(cause);
            }

        });
    }

    @Override
    public ClassicHttpResponse execute(ClassicHttpRequest request, ExecChain.Scope scope, ExecChain chain)
            throws IOException, HttpException {
        ApacheHttpClientContext observationContext = new ApacheHttpClientContext(request, scope.clientContext);
        Observation observation = ApacheHttpClientObservationDocumentation.DEFAULT
            .observation(observationConvention, DefaultApacheHttpClientObservationConvention.INSTANCE,
                    () -> observationContext, observationRegistry)
            .start();
        try {
            ClassicHttpResponse response = chain.proceed(request, scope);
            observationContext.setResponse(response);
            return response;
        }
        catch (Throwable exc) {
            observation.error(exc);
            throw exc;
        }
        finally {
            observation.stop();
        }
    }

    private static class ObservableCancellableDependency implements CancellableDependency {

        private final Observation observation;

        private final AtomicMarkableReference<Cancellable> dependencyRef;

        ObservableCancellableDependency(Observation observation) {
            this.observation = observation;
            this.dependencyRef = new AtomicMarkableReference<>(null, false);
        }

        @Override
        public boolean isCancelled() {
            return dependencyRef.isMarked();
        }

        @Override
        public void setDependency(Cancellable dependency) {
            Objects.requireNonNull(dependency, "dependency");
            Cancellable actualDependency = dependencyRef.getReference();
            if (!dependencyRef.compareAndSet(actualDependency, dependency, false, false)) {
                dependency.cancel();
            }
        }

        @Override
        public boolean cancel() {
            while (!dependencyRef.isMarked()) {
                Cancellable actualDependency = dependencyRef.getReference();
                if (dependencyRef.compareAndSet(actualDependency, actualDependency, false, true)) {
                    if (actualDependency != null) {
                        actualDependency.cancel();
                    }
                    this.observation.stop();
                    return true;
                }
            }
            this.observation.stop();
            return false;
        }

        AsyncExecChain.Scope wrapScope(AsyncExecChain.Scope scope) {
            scope.cancellableDependency.setDependency(this);
            return new AsyncExecChain.Scope(scope.exchangeId, scope.route, scope.originalRequest, this,
                    scope.clientContext, scope.execRuntime, scope.scheduler, scope.execCount);
        }

    }

}
