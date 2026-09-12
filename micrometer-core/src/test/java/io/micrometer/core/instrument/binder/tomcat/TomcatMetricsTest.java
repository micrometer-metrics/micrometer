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
import io.micrometer.core.instrument.Meter;
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
import org.apache.coyote.RequestGroupInfo;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final AtomicInteger TEST_DOMAIN_COUNTER = new AtomicInteger();

    // tag::setup[]
    SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    // end::setup[]

    private int port;

    private static String nextTestDomain() {
        return "TomcatMetricsTest-" + TEST_DOMAIN_COUNTER.incrementAndGet();
    }

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
                resp.getOutputStream().write("yes".getBytes(StandardCharsets.UTF_8));
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
                resp.getOutputStream().write("yes".getBytes(StandardCharsets.UTF_8));
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
                resp.getOutputStream().write("yes".getBytes(StandardCharsets.UTF_8));
            }
        }, new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8));
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
                resp.getOutputStream().write("yes".getBytes(StandardCharsets.UTF_8));
            }
        }, new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {
                IOUtils.toString(req.getInputStream());
                sleep();
                resp.getOutputStream().write("hi".getBytes(StandardCharsets.UTF_8));
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

    @Test
    @Issue("#7535")
    void globalRequestMetrics_areRegisteredForBothHttp11AndHttp2UpgradeMBeans() throws Exception {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        String domain = nextTestDomain();

        ObjectName http11 = new ObjectName(domain + ":type=GlobalRequestProcessor,name=\"http-nio-0\"");
        ObjectName http2 = new ObjectName(domain + ":type=GlobalRequestProcessor,name=\"http-nio-0\",Upgrade=\"h2c\"");

        try {
            mBeanServer.registerMBean(new FakeGlobalRequestProcessor(1, 2, 3, 4, 5, 6), http11);
            mBeanServer.registerMBean(new FakeGlobalRequestProcessor(7, 8, 9, 10, 11, 12), http2);

            try (TomcatMetrics binder = new TomcatMetrics(null, Tags.empty(), mBeanServer)) {
                binder.setJmxDomain(domain);
                binder.bindTo(registry);

                Collection<Meter> received = registry.find("tomcat.global.received").meters();
                assertThat(received)
                    .as("both the HTTP/1.1 and the HTTP/2 GlobalRequestProcessor MBeans must be exposed")
                    .hasSize(2);
                assertThat(received).extracting(m -> m.getId().getTag("name")).containsOnly("http-nio-0");
                assertThat(received).extracting(m -> m.getId().getTag("upgrade"))
                    .as("the upgrade tag distinguishes HTTP/2 from HTTP/1.1; HTTP/1.1 uses the 'none' placeholder so the label set stays consistent")
                    .containsExactlyInAnyOrder("none", "h2c");
            }
        }
        finally {
            safeUnregister(mBeanServer, http11);
            safeUnregister(mBeanServer, http2);
        }
    }

    @Test
    @Issue("#7535")
    void globalRequestMetrics_skipNonRequestGroupInfoMBeans() throws Exception {
        // Tomcat registers UpgradeGroupInfo (servlet upgrades such as WebSocket) under
        // the
        // same :type=GlobalRequestProcessor,name=...,Upgrade=... ObjectName shape used by
        // HTTP/2's RequestGroupInfo. The binder must skip the former — its attribute set
        // is incompatible and would produce NaN/0 series.
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        String domain = nextTestDomain();

        ObjectName websocket = new ObjectName(
                domain + ":type=GlobalRequestProcessor,name=\"http-nio-0\",Upgrade=\"websocket\"");

        try {
            mBeanServer.registerMBean(new FakeUpgradeGroupInfo(), websocket);

            try (TomcatMetrics binder = new TomcatMetrics(null, Tags.empty(), mBeanServer)) {
                binder.setJmxDomain(domain);
                binder.bindTo(registry);

                assertThat(registry.find("tomcat.global.received").meters())
                    .as("MBeans matching the ObjectName pattern but not RequestGroupInfo must be skipped")
                    .isEmpty();
                assertThat(registry.find("tomcat.global.request").meters()).isEmpty();
                assertThat(registry.find("tomcat.global.error").meters()).isEmpty();
            }
        }
        finally {
            safeUnregister(mBeanServer, websocket);
        }
    }

    @Test
    @Issue("#7535")
    void globalRequestMetrics_areRegisteredEventuallyForLateMBeans() throws Exception {
        // Exercises the MBeanServer notification-listener path: bindTo runs before any
        // matching MBean is present, so the registration must happen via the listener
        // when the per-protocol MBeans appear later.
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        String domain = nextTestDomain();

        ObjectName http11 = new ObjectName(domain + ":type=GlobalRequestProcessor,name=\"http-nio-0\"");
        ObjectName http2 = new ObjectName(domain + ":type=GlobalRequestProcessor,name=\"http-nio-0\",Upgrade=\"h2c\"");

        CountDownLatch latch = new CountDownLatch(2);
        registry.config().onMeterAdded(m -> {
            if ("tomcat.global.received".equals(m.getId().getName())) {
                latch.countDown();
            }
        });

        try (TomcatMetrics binder = new TomcatMetrics(null, Tags.empty(), mBeanServer)) {
            // setJmxDomain bypasses the embedded/standalone autodetect that would
            // otherwise fail without a real Tomcat MBean present
            binder.setJmxDomain(domain);
            binder.bindTo(registry);

            try {
                mBeanServer.registerMBean(new FakeGlobalRequestProcessor(1, 2, 3, 4, 5, 6), http11);
                mBeanServer.registerMBean(new FakeGlobalRequestProcessor(7, 8, 9, 10, 11, 12), http2);

                assertThat(latch.await(5, TimeUnit.SECONDS))
                    .as("the registration-notification path must register meters for both protocols")
                    .isTrue();

                Collection<Meter> received = registry.find("tomcat.global.received").meters();
                assertThat(received).hasSize(2);
                assertThat(received).extracting(m -> m.getId().getTag("upgrade"))
                    .containsExactlyInAnyOrder("none", "h2c");
            }
            finally {
                safeUnregister(mBeanServer, http11);
                safeUnregister(mBeanServer, http2);
            }
        }
    }

    private static void safeUnregister(MBeanServer mBeanServer, ObjectName name) throws Exception {
        if (mBeanServer.isRegistered(name)) {
            mBeanServer.unregisterMBean(name);
        }
    }

    public interface FakeGlobalRequestProcessorMBean {

        long getBytesSent();

        long getBytesReceived();

        int getRequestCount();

        int getErrorCount();

        long getProcessingTime();

        long getMaxTime();

    }

    /**
     * Registered as a Standard MBean via {@link FakeGlobalRequestProcessorMBean} so the
     * fixture lives in the platform server without a running Tomcat. The Standard MBean
     * convention exposes the attribute as {@code RequestCount} (capitalized); the
     * production {@code isRequestGroupInfo} guard uses
     * {@code equalsIgnoreCase("requestCount")}, which matches both this naming and
     * Tomcat's {@code BaseModelMBean} wrapping (lowercase {@code requestCount}).
     * Extending {@link RequestGroupInfo} is only to align getter return types with the
     * parent so the overrides compile ({@code int} for request/error counts).
     */
    public static class FakeGlobalRequestProcessor extends RequestGroupInfo implements FakeGlobalRequestProcessorMBean {

        private final long bytesSent;

        private final long bytesReceived;

        private final int requestCount;

        private final int errorCount;

        private final long processingTime;

        private final long maxTime;

        public FakeGlobalRequestProcessor(long bytesSent, long bytesReceived, int requestCount, int errorCount,
                long processingTime, long maxTime) {
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.requestCount = requestCount;
            this.errorCount = errorCount;
            this.processingTime = processingTime;
            this.maxTime = maxTime;
        }

        @Override
        public long getBytesSent() {
            return bytesSent;
        }

        @Override
        public long getBytesReceived() {
            return bytesReceived;
        }

        @Override
        public int getRequestCount() {
            return requestCount;
        }

        @Override
        public int getErrorCount() {
            return errorCount;
        }

        @Override
        public long getProcessingTime() {
            return processingTime;
        }

        @Override
        public long getMaxTime() {
            return maxTime;
        }

    }

    public interface FakeUpgradeGroupInfoMBean {

        long getBytesSent();

        long getBytesReceived();

        long getMsgsSent();

        long getMsgsReceived();

    }

    /**
     * Mirrors the shape of Tomcat's {@code UpgradeGroupInfo} (used by WebSocket and other
     * servlet upgrades): same ObjectName pattern as the per-connector aggregator, but a
     * different attribute set. Intentionally does NOT extend {@link RequestGroupInfo} so
     * the binder's guard skips it.
     */
    public static class FakeUpgradeGroupInfo implements FakeUpgradeGroupInfoMBean {

        @Override
        public long getBytesSent() {
            return 0;
        }

        @Override
        public long getBytesReceived() {
            return 0;
        }

        @Override
        public long getMsgsSent() {
            return 0;
        }

        @Override
        public long getMsgsReceived() {
            return 0;
        }

    }

}
