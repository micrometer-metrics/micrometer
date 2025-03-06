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

import io.micrometer.common.KeyValue;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.RequestFailedException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micrometer.core.instrument.binder.httpcomponents.hc5.ApacheHttpClientObservationDocumentation.ApacheHttpClientKeyNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ObservationExecChainHandler}.
 *
 * @author Brian Clozel
 */
class ObservationExecChainHandlerTest {

    private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

    private final ObservationExecChainHandler handler = new ObservationExecChainHandler(observationRegistry);

    private final ClassicHttpRequest request = new HttpGet("");

    private final HttpRequest asyncRequest = SimpleHttpRequest.create("GET", "https://example.org");

    private final HttpGet cancellable = new HttpGet("https://example.org");

    private final HttpClientContext clientContext = HttpClientContext.create();

    @Nested
    class ClassicClientTests {

        private ExecChain.Scope scope;

        @BeforeEach
        void setup() throws Exception {
            this.scope = new ExecChain.Scope("id", new HttpRoute(HttpHost.create("https://example.org:8080")),
                    cancellable, mock(ExecRuntime.class), clientContext);
        }

        @Test
        void shouldInstrumentReceivedResponses() throws Exception {
            ExecChain chain = mock(ExecChain.class);
            given(chain.proceed(any(), any())).willReturn(new BasicClassicHttpResponse(200));
            handler.execute(request, this.scope, chain);
            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SUCCESS"));
        }

        @Test
        void shouldInstrumentExceptions() throws Exception {
            ExecChain chain = mock(ExecChain.class);
            given(chain.proceed(any(), any())).willThrow(new IllegalArgumentException());

            assertThatThrownBy(() -> handler.execute(request, this.scope, chain))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("IllegalArgumentException"));
        }

        @Test
        void shouldInstrumentCancelledRequests() throws Exception {
            ExecChain chain = mock(ExecChain.class);
            given(chain.proceed(any(), any())).willThrow(new RequestFailedException("request cancelled"));

            assertThatThrownBy(() -> handler.execute(request, this.scope, chain))
                .isInstanceOf(RequestFailedException.class);
            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("RequestFailedException"));
        }

    }

    @Nested
    class AsyncClientTests {

        private AsyncExecChain.Scope scope;

        private final TestAsyncExecChain testAsyncExecChain = new TestAsyncExecChain();

        @BeforeEach
        void setup() throws Exception {
            this.scope = new AsyncExecChain.Scope("id", new HttpRoute(HttpHost.create("https://example.org:8080")),
                    asyncRequest, cancellable, clientContext, mock(AsyncExecRuntime.class),
                    mock(AsyncExecChain.Scheduler.class), new AtomicInteger(1));
        }

        @Test
        void shouldInstrumentReceivedResponses() throws Exception {
            AsyncExecCallback clientCallback = mock(AsyncExecCallback.class);
            handler.execute(request, null, this.scope, testAsyncExecChain, clientCallback);

            testAsyncExecChain.receivedCallback.handleResponse(new BasicClassicHttpResponse(200), null);
            verify(clientCallback).handleResponse(any(), any());

            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("SUCCESS"));
        }

        @Test
        void shouldInstrumentExceptions() throws Exception {
            AsyncExecCallback clientCallback = mock(AsyncExecCallback.class);
            handler.execute(request, null, this.scope, testAsyncExecChain, clientCallback);

            testAsyncExecChain.receivedCallback.failed(new IllegalArgumentException());
            verify(clientCallback).failed(any());

            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue("IllegalArgumentException"));
        }

        @Test
        void shouldInstrumentCancelledRequests() throws Exception {
            AsyncExecCallback clientCallback = mock(AsyncExecCallback.class);
            handler.execute(request, null, this.scope, testAsyncExecChain, clientCallback);

            cancellable.cancel();

            assertThat(observationRegistry).hasObservationWithNameEqualTo("httpcomponents.httpclient.request")
                .that()
                .hasLowCardinalityKeyValue(OUTCOME.withValue("UNKNOWN"))
                .hasLowCardinalityKeyValue(EXCEPTION.withValue(KeyValue.NONE_VALUE));
        }

    }

    static class TestAsyncExecChain implements AsyncExecChain {

        @Nullable
        AsyncExecCallback receivedCallback;

        @Override
        public void proceed(HttpRequest request, AsyncEntityProducer entityProducer, Scope scope,
                AsyncExecCallback asyncExecCallback) throws HttpException, IOException {
            this.receivedCallback = asyncExecCallback;
        }

    }

}
