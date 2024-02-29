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
package io.micrometer.jetty11;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.http.Outcome;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ported from
 * https://github.com/eclipse/jetty.project/blob/jetty-9.4.x/jetty-server/src/test/java/org/eclipse/jetty/server/handler/StatisticsHandlerTest.java
 */
@SuppressWarnings("deprecation")
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
    void tearDown() throws Exception {
        server.stop();
        server.join();
    }

    @Test
    void testRequest() throws Exception {
        CyclicBarrier[] barrier = { new CyclicBarrier(3), new CyclicBarrier(3) };
        latchHandler.reset(2);

        timedHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String path, Request request, HttpServletRequest httpRequest,
                    HttpServletResponse httpResponse) throws IOException {
                request.setHandled(true);
                try {
                    barrier[0].await(5, TimeUnit.SECONDS);
                    barrier[1].await(5, TimeUnit.SECONDS);
                }
                catch (Exception x) {
                    Thread.currentThread().interrupt();
                    throw new IOException(x);
                }
            }
        });
        server.start();

        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
        connector.executeRequest(request);
        connector.executeRequest(request);

        barrier[0].await(5, TimeUnit.SECONDS);
        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(2);

        barrier[1].await(5, TimeUnit.SECONDS);
        assertThat(latchHandler.await()).isTrue();

        assertThat(registry.get("jetty.server.requests")
            .tag("outcome", Outcome.SUCCESS.name())
            .tag("method", "GET")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(2);
    }

    @Test
    void testSuspendResume() throws Exception {
        long dispatchTime = 10;
        long requestTime = 50;
        AtomicReference<AsyncContext> asyncHolder = new AtomicReference<>();
        CyclicBarrier[] barrier = { new CyclicBarrier(2), new CyclicBarrier(2), new CyclicBarrier(2) };

        timedHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String path, Request request, HttpServletRequest httpRequest,
                    HttpServletResponse httpResponse) throws ServletException {
                request.setHandled(true);
                try {
                    barrier[0].await(5, TimeUnit.SECONDS);

                    Thread.sleep(dispatchTime);

                    if (asyncHolder.get() == null) {
                        asyncHolder.set(request.startAsync());
                    }
                }
                catch (Exception x) {
                    throw new ServletException(x);
                }
                finally {
                    try {
                        barrier[1].await(5, TimeUnit.SECONDS);
                    }
                    catch (Exception ignored) {
                    }
                }
            }
        });

        server.start();

        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
        connector.executeRequest(request);

        barrier[0].await(5, TimeUnit.SECONDS);

        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(1);

        barrier[1].await(5, TimeUnit.SECONDS);
        assertThat(latchHandler.await()).isTrue();
        assertThat(asyncHolder.get()).isNotNull();

        latchHandler.reset();
        barrier[0].reset();
        barrier[1].reset();

        Thread.sleep(requestTime);

        asyncHolder.get().addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
                try {
                    barrier[2].await(5, TimeUnit.SECONDS);
                }
                catch (Exception ignored) {
                }
            }
        });
        asyncHolder.get().dispatch();

        barrier[0].await(5, TimeUnit.SECONDS); // entered app handler
        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(1);

        barrier[1].await(5, TimeUnit.SECONDS); // exiting app handler
        assertThat(latchHandler.await()).isTrue(); // exited timed handler

        barrier[2].await(5, TimeUnit.SECONDS); // onComplete called
        assertThat(registry.get("jetty.server.requests")
            .tag("outcome", Outcome.SUCCESS.name())
            .tag("method", "GET")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void testSuspendExpire() throws Exception {
        long dispatchTime = 10;
        long timeout = 100;
        AtomicReference<AsyncContext> asyncHolder = new AtomicReference<>();
        CyclicBarrier[] barrier = { new CyclicBarrier(2), new CyclicBarrier(2), new CyclicBarrier(2) };

        timedHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String path, Request request, HttpServletRequest httpRequest,
                    HttpServletResponse httpResponse) throws ServletException {
                request.setHandled(true);
                try {
                    barrier[0].await(5, TimeUnit.SECONDS);

                    Thread.sleep(dispatchTime);

                    if (asyncHolder.get() == null) {
                        AsyncContext async = request.startAsync();
                        asyncHolder.set(async);
                        async.setTimeout(timeout);
                    }
                }
                catch (Exception x) {
                    throw new ServletException(x);
                }
                finally {
                    try {
                        barrier[1].await(5, TimeUnit.SECONDS);
                    }
                    catch (Exception ignored) {
                    }
                }
            }
        });
        server.start();

        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
        connector.executeRequest(request);

        barrier[0].await(5, TimeUnit.SECONDS);

        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(1);

        barrier[1].await(5, TimeUnit.SECONDS);
        assertThat(latchHandler.await()).isTrue();
        assertThat(asyncHolder.get()).isNotNull();

        asyncHolder.get().addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
                event.getAsyncContext().complete();
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
                try {
                    barrier[2].await(5, TimeUnit.SECONDS);
                }
                catch (Exception ignored) {
                }
            }
        });

        barrier[2].await(5, TimeUnit.SECONDS);

        assertThat(registry.get("jetty.server.async.expires").counter().count()).isEqualTo(1);
        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(0);
    }

    @Test
    void testSuspendComplete() throws Exception {
        long dispatchTime = 10;
        AtomicReference<AsyncContext> asyncHolder = new AtomicReference<>();
        CyclicBarrier[] barrier = { new CyclicBarrier(2), new CyclicBarrier(2) };
        CountDownLatch latch = new CountDownLatch(1);

        timedHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String path, Request request, HttpServletRequest httpRequest,
                    HttpServletResponse httpResponse) throws ServletException {
                request.setHandled(true);
                try {
                    barrier[0].await(5, TimeUnit.SECONDS);

                    Thread.sleep(dispatchTime);

                    if (asyncHolder.get() == null) {
                        AsyncContext async = request.startAsync();
                        asyncHolder.set(async);
                    }
                }
                catch (Exception x) {
                    throw new ServletException(x);
                }
                finally {
                    try {
                        barrier[1].await(5, TimeUnit.SECONDS);
                    }
                    catch (Exception ignored) {
                    }
                }
            }
        });
        server.start();

        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
        connector.executeRequest(request);

        barrier[0].await(5, TimeUnit.SECONDS);
        assertThat(registry.get("jetty.server.dispatches.open").longTaskTimer().activeTasks()).isEqualTo(1);

        barrier[1].await(5, TimeUnit.SECONDS);
        assertThat(latchHandler.await()).isTrue();
        assertThat(asyncHolder.get()).isNotNull();

        asyncHolder.get().addListener(new AsyncListener() {
            @Override
            public void onTimeout(AsyncEvent event) {
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
            }

            @Override
            public void onError(AsyncEvent event) {
            }

            @Override
            public void onComplete(AsyncEvent event) {
                try {
                    latch.countDown();
                }
                catch (Exception ignored) {
                }
            }
        });
        long requestTime = 20;
        Thread.sleep(requestTime);
        asyncHolder.get().complete();
        latch.await(5, TimeUnit.SECONDS);

        assertThat(registry.get("jetty.server.requests")
            .tag("outcome", Outcome.SUCCESS.name())
            .tag("method", "GET")
            .tag("status", "200")
            .timer()
            .count()).isEqualTo(1);
    }

    @Test
    void testAsyncRequestWithShutdown() throws Exception {
        long delay = 500;
        CountDownLatch serverLatch = new CountDownLatch(1);
        timedHandler.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                    HttpServletResponse response) {
                AsyncContext asyncContext = request.startAsync();
                asyncContext.setTimeout(0);
                new Thread(() -> {
                    try {
                        Thread.sleep(delay);
                        asyncContext.complete();
                    }
                    catch (InterruptedException e) {
                        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
                        asyncContext.complete();
                    }
                }).start();
                serverLatch.countDown();
            }
        });
        server.start();

        String request = "GET / HTTP/1.1\r\n" + "Host: localhost\r\n" + "\r\n";
        connector.executeRequest(request);

        assertThat(serverLatch.await(5, TimeUnit.SECONDS)).isTrue();

        Future<Void> shutdown = timedHandler.shutdown();
        assertThat(shutdown.isDone()).isFalse();

        Thread.sleep(delay / 2);
        assertThat(shutdown.isDone()).isFalse();

        Thread.sleep(delay);
        assertThat(shutdown.isDone()).isTrue();
    }

    private static class LatchHandler extends HandlerWrapper {

        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void handle(String path, Request request, HttpServletRequest httpRequest,
                HttpServletResponse httpResponse) throws IOException, ServletException {
            try {
                super.handle(path, request, httpRequest, httpResponse);
            }
            finally {
                latch.countDown();
            }
        }

        private void reset() {
            reset(1);
        }

        private void reset(int count) {
            latch = new CountDownLatch(count);
        }

        private boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

    }

}
