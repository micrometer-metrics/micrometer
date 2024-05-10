/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.jetty12.server;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.Graceful;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing Jetty 12 TimedHandler
 */
class TimedHandlerTest {

    private SimpleMeterRegistry registry;

    private TimedHandler timedHandler;

    private Server server;

    private LocalConnector connector;

    private LatchHandler latchHandler;

    @BeforeEach
    void setup() {
        this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        this.timedHandler = new TimedHandler(registry, Tags.empty());

        this.server = new Server();
        this.connector = new LocalConnector(server);
        server.addConnector(connector);

        latchHandler = new LatchHandler();

        server.setHandler(latchHandler);
        latchHandler.setHandler(timedHandler);
    }

    @AfterEach
    void tearDown() {
        LifeCycle.stop(server);
    }

    @Test
    void testRequest() throws Exception {
        CyclicBarrier[] barrier = { new CyclicBarrier(3), new CyclicBarrier(3) };
        latchHandler.reset(2);

        timedHandler.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                try {
                    response.setStatus(200);
                    barrier[0].await(5, TimeUnit.SECONDS);
                    barrier[1].await(5, TimeUnit.SECONDS);
                    response.write(true, BufferUtil.EMPTY_BUFFER, callback);
                }
                catch (Exception e) {
                    Thread.currentThread().interrupt();
                    callback.failed(e);
                }
                return true;
            }
        });
        server.start();

        try (LocalConnector.LocalEndPoint endpoint1 = connector.connect();
                LocalConnector.LocalEndPoint endpoint2 = connector.connect()) {
            // Initiate two requests, on different endpoints to avoid HTTP/1.1 persistent
            // connection behaviors.
            String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
            endpoint1.addInputAndExecute(request);
            endpoint2.addInputAndExecute(request);

            barrier[0].await(5, TimeUnit.SECONDS);
            assertThat(registry.get("jetty.server.requests.open").longTaskTimer().activeTasks()).isEqualTo(2);

            barrier[1].await(5, TimeUnit.SECONDS);
            assertThat(latchHandler.await()).isTrue();

            // Read the two responses to ensure that they are complete
            HttpTester.Response response1 = HttpTester.parseResponse(endpoint1.getResponse());
            assertThat(response1.getStatus()).isEqualTo(HttpStatus.OK_200);
            assertThat(response1.getContent()).isEmpty();
            HttpTester.Response response2 = HttpTester.parseResponse(endpoint2.getResponse());
            assertThat(response2.getStatus()).isEqualTo(HttpStatus.OK_200);
            assertThat(response2.getContent()).isEmpty();

            assertThat(registry.get("jetty.server.requests")
                .tag("outcome", Outcome.SUCCESS.name())
                .tag("method", "GET")
                .tag("status", "200")
                .timer()
                .count()).isEqualTo(2);
        }
    }

    @Test
    void testRequestWithShutdown() throws Exception {
        long delay = 500;
        CountDownLatch serverLatch = new CountDownLatch(1);
        timedHandler.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.setStatus(200);
                // commit response
                response.write(false, BufferUtil.EMPTY_BUFFER, Callback.NOOP);
                new Thread(() -> {
                    // let test proceed
                    serverLatch.countDown();
                    try {
                        // wait on finishing the response
                        Thread.sleep(delay);
                    }
                    catch (InterruptedException e) {
                        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                        callback.failed(e);
                        return;
                    }
                    // finish response
                    response.write(true, BufferUtil.EMPTY_BUFFER, callback);
                }).start();
                return true;
            }
        });
        server.start();

        try (LocalConnector.LocalEndPoint endpoint = connector.connect()) {
            String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
            endpoint.addInputAndExecute(request);

            // wait till we reach the Handler
            assertThat(serverLatch.await(5, TimeUnit.SECONDS)).isTrue();

            // initiate a shutdown
            Future<Void> shutdownFuture = timedHandler.shutdown();
            Graceful.Shutdown shutdown = timedHandler.getShutdown();
            assertThat(shutdownFuture.isDone()).isFalse();

            // delay half what the handler is sleeping
            Thread.sleep(delay / 2);
            // response is still active, so don't shutdown.
            shutdown.check();
            assertThat(shutdownFuture.isDone()).isFalse();

            // Read response to ensure it is done
            HttpTester.Response response1 = HttpTester.parseResponse(endpoint.getResponse());
            assertThat(response1.getStatus()).isEqualTo(HttpStatus.OK_200);
            assertThat(response1.getContent()).isEmpty();

            Thread.sleep(delay);
            shutdown.check();
            assertThat(shutdownFuture.isDone()).isTrue();
        }
    }

    private static class LatchHandler extends Handler.Wrapper {

        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            try {
                return super.handle(request, response, callback);
            }
            finally {
                latch.countDown();
            }
        }

        private void reset(int count) {
            latch = new CountDownLatch(count);
        }

        private boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

    }

}
