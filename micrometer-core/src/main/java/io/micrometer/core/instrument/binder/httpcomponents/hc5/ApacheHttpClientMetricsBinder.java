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
import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import java.util.Objects;

public class ApacheHttpClientMetricsBinder {

    public static final String INTERCEPTOR_NAME = "micrometer";

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ApacheHttpClientObservationConvention observationConvention;

    private final boolean meterRetries;

    private ApacheHttpClientMetricsBinder(ObservationRegistry observationRegistry,
            @Nullable ApacheHttpClientObservationConvention observationConvention, boolean meterRetries) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry);
        this.observationConvention = observationConvention;
        this.meterRetries = meterRetries;
    }

    public void instrument(HttpClientBuilder clientBuilder) {
        Objects.requireNonNull(clientBuilder);
        final ObservationExecChainHandler execChainHandler = new ObservationExecChainHandler(observationRegistry,
                observationConvention, meterRetries);
        if (meterRetries) {
            clientBuilder.addExecInterceptorAfter(ChainElement.RETRY.name(), INTERCEPTOR_NAME, execChainHandler);
        }
        else {
            clientBuilder.addExecInterceptorFirst(INTERCEPTOR_NAME, execChainHandler);
        }
    }

    /**
     * Instrument the clientBuilder and immediately build the client.
     * @param clientBuilder - the clientBuilder to instrument
     * @return the fully configured CloseableHttpClient
     */
    public CloseableHttpClient instrumentAndGet(HttpClientBuilder clientBuilder) {
        instrument(clientBuilder);
        return clientBuilder.build();
    }

    public void instrument(HttpAsyncClientBuilder asyncClientBuilder) {
        Objects.requireNonNull(asyncClientBuilder);
        final ObservationExecChainHandler execChainHandler = new ObservationExecChainHandler(observationRegistry,
                observationConvention, meterRetries);
        if (meterRetries) {
            asyncClientBuilder.addExecInterceptorAfter(ChainElement.RETRY.name(), INTERCEPTOR_NAME, execChainHandler);
        }
        else {
            asyncClientBuilder.addExecInterceptorFirst(INTERCEPTOR_NAME, execChainHandler);
        }
    }

    /**
     * Instrument the asyncClientBuilder and immediately build the client.
     * @param asyncClientBuilder - the asyncClientBuilder to instrument
     * @return the fully configured CloseableHttpAsyncClient
     */
    public CloseableHttpAsyncClient instrumentAndGet(HttpAsyncClientBuilder asyncClientBuilder) {
        instrument(asyncClientBuilder);
        return asyncClientBuilder.build();
    }

    public static ApacheHttpClientMetricsBinder.Builder builder(ObservationRegistry registry) {
        return new ApacheHttpClientMetricsBinder.Builder(registry);
    }

    public static class Builder {

        private final ObservationRegistry observationRegistry;

        @Nullable
        private ApacheHttpClientObservationConvention observationConvention;

        private boolean meterRetries = false;

        Builder(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
        }

        public ApacheHttpClientMetricsBinder.Builder observationConvention(
                ApacheHttpClientObservationConvention observationConvention) {
            this.observationConvention = observationConvention;
            return this;
        }

        public ApacheHttpClientMetricsBinder.Builder meterRetries(boolean meterRetries) {
            this.meterRetries = meterRetries;
            return this;
        }

        public ApacheHttpClientMetricsBinder build() {
            return new ApacheHttpClientMetricsBinder(observationRegistry, observationConvention, meterRetries);
        }

    }

}
