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
package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.IOUtils;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.apache.catalina.startup.Tomcat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link TomcatMetrics}.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 * @author Johnny Lim
 */
class TomcatMetricsTest {

    private static final int PROCESSING_TIME_IN_MILLIS = 10;

    // tag::setup[]
    SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    // end::setup[]

    private int port;

    @Test
    void managerBasedMetrics() {
        Context context = new StandardContext();

        ManagerBase manager = new ManagerBase() {
            @Override
            public void load() {
            }

            @Override
            public void unload() {
            }

            @Override
            public Context getContext() {
                return context;
            }
        };

        manager.setMaxActiveSessions(3);

        manager.createSession("first");
        manager.createSession("second");
        manager.createSession("third");

        try {
            manager.createSession("fourth");
            fail("TooManyActiveSessionsException expected.");
        }
        catch (TooManyActiveSessionsException exception) {
            // ignore error, testing rejection
        }

        StandardSession expiredSession = new StandardSession(manager);
        expiredSession.setId("third");
        expiredSession.setCreationTime(System.currentTimeMillis() - 10_000);
        manager.remove(expiredSession, true);

        // tag::monitor[]
        Iterable<Tag> tags = Tags.of("metricTag", "val1");
        TomcatMetrics.monitor(registry, manager, tags);
        // end::monitor[]

        // tag::example[]
        assertThat(registry.get("tomcat.sessions.active.max").tags(tags).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("tomcat.sessions.active.current").tags(tags).gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("tomcat.sessions.expired").tags(tags).functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.sessions.rejected").tags(tags).functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.sessions.created").tags(tags).functionCounter().count()).isEqualTo(3.0);
        assertThat(registry.get("tomcat.sessions.alive.max").tags(tags).timeGauge().value()).isGreaterThan(1.0);
        // end::example[]
    }

    private void sleep() {
        try {
            Thread.sleep(PROCESSING_TIME_IN_MILLIS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void whenTomcatMetricsBoundBeforeTomcatStarted_mbeanMetricsRegisteredEventually() throws Exception {
        TomcatMetrics.monitor(registry, null);

        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().equals("tomcat.global.received"))
                latch.countDown();
        });

        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("yes".getBytes());
            }
        };

        runTomcat(servlet, () -> {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

            checkMbeansInitialState();

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/0");
                post.setEntity(new StringEntity("you there?"));
                CloseableHttpResponse response1 = httpClient.execute(post);

                CloseableHttpResponse response2 = httpClient
                    .execute(new HttpGet("http://localhost:" + this.port + "/0/no-get"));

                long expectedSentBytes = response1.getEntity().getContentLength()
                        + response2.getEntity().getContentLength();
                checkMbeansAfterRequests(expectedSentBytes);
            }

            return null;
        });
    }

    @Test
    void whenTomcatMetricsBoundAfterTomcatStarted_mbeanMetricsRegisteredImmediately() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("yes".getBytes());
            }
        };

        runTomcat(servlet, () -> {
            TomcatMetrics.monitor(registry, null);

            checkMbeansInitialState();

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/0");
                post.setEntity(new StringEntity("you there?"));
                CloseableHttpResponse response1 = httpClient.execute(post);

                CloseableHttpResponse response2 = httpClient
                    .execute(new HttpGet("http://localhost:" + this.port + "/0/no-get"));

                long expectedSentBytes = response1.getEntity().getContentLength()
                        + response2.getEntity().getContentLength();
                checkMbeansAfterRequests(expectedSentBytes);
            }

            return null;
        });
    }

    @Test
    @Issue("#1989")
    void whenMultipleServlets_thenRegisterMetricsForAllServlets() throws Exception {
        Collection<Servlet> servlets = Arrays.asList(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("yes".getBytes());
            }
        }, new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("hi".getBytes());
            }
        });

        runTomcat(servlets, () -> {
            TomcatMetrics.monitor(registry, null);

            checkMbeansInitialState();

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/0");
                post.setEntity(new StringEntity("you there?"));
                CloseableHttpResponse response1 = httpClient.execute(post);

                CloseableHttpResponse response2 = httpClient
                    .execute(new HttpGet("http://localhost:" + this.port + "/1"));

                FunctionTimer servlet0 = registry.get("tomcat.servlet.request").tag("name", "servlet0").functionTimer();
                FunctionTimer servlet1 = registry.get("tomcat.servlet.request").tag("name", "servlet1").functionTimer();
                assertThat(servlet0.count()).isEqualTo(1);
                assertThat(servlet0.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
                assertThat(servlet1.count()).isEqualTo(1);
                assertThat(servlet1.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
            }

            return null;
        });
    }

    @Test
    @Issue("#1989")
    void whenMultipleServletsAndTomcatMetricsBoundBeforeTomcatStarted_thenEventuallyRegisterMetricsForAllServlets()
            throws Exception {
        TomcatMetrics.monitor(registry, null);
        CountDownLatch latch0 = new CountDownLatch(1);
        CountDownLatch latch1 = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().equals("tomcat.servlet.error")) {
                if ("servlet0".equals(m.getId().getTag("name"))) {
                    latch0.countDown();
                }
                else if ("servlet1".equals(m.getId().getTag("name"))) {
                    latch1.countDown();
                }
            }
        });

        Collection<Servlet> servlets = Arrays.asList(new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("yes".getBytes());
            }
        }, new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("hi".getBytes());
            }
        });

        runTomcat(servlets, () -> {
            assertThat(latch0.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(latch1.await(3, TimeUnit.SECONDS)).isTrue();

            checkMbeansInitialState();

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/0");
                post.setEntity(new StringEntity("you there?"));
                CloseableHttpResponse response1 = httpClient.execute(post);

                CloseableHttpResponse response2 = httpClient
                    .execute(new HttpGet("http://localhost:" + this.port + "/1"));

                FunctionTimer servlet0 = registry.get("tomcat.servlet.request").tag("name", "servlet0").functionTimer();
                FunctionTimer servlet1 = registry.get("tomcat.servlet.request").tag("name", "servlet1").functionTimer();
                assertThat(servlet0.count()).isEqualTo(1);
                assertThat(servlet0.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
                assertThat(servlet1.count()).isEqualTo(1);
                assertThat(servlet1.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
            }

            return null;
        });
    }

    void runTomcat(HttpServlet servlet, Callable<Void> doWithTomcat) throws Exception {
        runTomcat(Collections.singleton(servlet), doWithTomcat);
    }

    void runTomcat(Collection<Servlet> servlets, Callable<Void> doWithTomcat) throws Exception {
        Tomcat server = new Tomcat();
        try {
            StandardHost host = new StandardHost();
            host.setName("localhost");
            server.setHost(host);
            server.setPort(0);
            server.start();

            this.port = server.getConnector().getLocalPort();

            Context context = server.addContext("", null);
            int i = 0;
            for (Servlet servlet : servlets) {
                server.addServlet("", "servlet" + i, servlet);
                context.addServletMappingDecoded("/" + i + "/*", "servlet" + i);
                i++;
            }

            doWithTomcat.call();

        }
        finally {
            server.stop();
            server.destroy();

        }
    }

    private void checkMbeansInitialState() {
        assertThat(registry.get("tomcat.global.sent").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.received").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.error").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().totalTime(TimeUnit.MILLISECONDS))
            .isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request.max").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.threads.busy").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.current").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.connections.current").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.connections.keepalive.current").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.connections.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.cache.access").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.cache.hit").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.servlet.error").functionCounter().count()).isEqualTo(0.0);
    }

    private void checkMbeansAfterRequests(long expectedSentBytes) {
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> registry.get("tomcat.global.sent").functionCounter().count() == expectedSentBytes);
        assertThat(registry.get("tomcat.global.received").functionCounter().count()).isEqualTo(10.0);
        assertThat(registry.get("tomcat.global.error").functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().count()).isEqualTo(2.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().totalTime(TimeUnit.MILLISECONDS))
            .isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
        assertThat(registry.get("tomcat.global.request.max").timeGauge().value(TimeUnit.MILLISECONDS))
            .isGreaterThanOrEqualTo(PROCESSING_TIME_IN_MILLIS);
        assertThat(registry.get("tomcat.threads.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.threads.busy").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.current").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.connections.current").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.connections.keepalive.current").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.connections.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.cache.access").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.cache.hit").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.servlet.error").functionCounter().count()).isEqualTo(1.0);
    }

}
