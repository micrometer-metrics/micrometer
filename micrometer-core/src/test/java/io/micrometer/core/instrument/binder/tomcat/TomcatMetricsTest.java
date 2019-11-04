/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.tomcat;

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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


/**
 * Tests for {@link TomcatMetrics}.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 * @author Johnny Lim
 */
class TomcatMetricsTest {
    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private int port;

    @BeforeEach
    void setUp() throws IOException {
        this.port = getAvailablePort();
    }

    private int getAvailablePort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
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
        } catch (TooManyActiveSessionsException exception) {
            //ignore error, testing rejection
        }

        StandardSession expiredSession = new StandardSession(manager);
        expiredSession.setId("third");
        expiredSession.setCreationTime(System.currentTimeMillis() - 10_000);
        manager.remove(expiredSession, true);

        Iterable<Tag> tags = Tags.of("metricTag", "val1");
        TomcatMetrics.monitor(registry, manager, tags);

        assertThat(registry.get("tomcat.sessions.active.max").tags(tags).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("tomcat.sessions.active.current").tags(tags).gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("tomcat.sessions.expired").tags(tags).functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.sessions.rejected").tags(tags).functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.sessions.created").tags(tags).functionCounter().count()).isEqualTo(3.0);
        assertThat(registry.get("tomcat.sessions.alive.max").tags(tags).timeGauge().value()).isGreaterThan(1.0);
    }

    @Test
    void mbeansAvailableAfterBinder() throws Exception {
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

                resp.getOutputStream().write("yes".getBytes());
            }
        };

        runTomcat(servlet, () -> {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

            checkMbeansInitialState();

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/");
                post.setEntity(new StringEntity("you there?"));
                httpClient.execute(post);

                httpClient.execute(new HttpGet("http://localhost:" + this.port + "/nowhere"));
            }

            checkMbeansAfterRequests();

            return null;
        });
    }
    @Test
    void mbeansAvailableBeforeBinder() throws Exception {
        HttpServlet servlet = new HttpServlet() {
            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                IOUtils.toString(req.getInputStream());

                resp.getOutputStream().write("yes".getBytes());
            }
        };

        runTomcat(servlet, () -> {
            TomcatMetrics.monitor(registry, null);

            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://localhost:" + this.port + "/");
                post.setEntity(new StringEntity("you there?"));
                httpClient.execute(post);

                httpClient.execute(new HttpGet("http://localhost:" + this.port + "/nowhere"));
            }

            checkMbeansAfterRequests();

            return null;
        });
    }


    void runTomcat(HttpServlet servlet, Callable<Void> doWithTomcat) throws Exception {
        Tomcat server = new Tomcat();
        try {
            StandardHost host = new StandardHost();
            host.setName("localhost");
            server.setHost(host);
            server.setPort(this.port);
            server.start();

            Context context = server.addContext("/", null);
            server.addServlet("/", "servletname", servlet);
            context.addServletMappingDecoded("/", "servletname");

            doWithTomcat.call();

        } finally {
            server.stop();
            server.destroy();

        }
    }

    private void checkMbeansInitialState() {
        assertThat(registry.get("tomcat.global.sent").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.received").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.error").functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().count()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(0.0);
        assertThat(registry.get("tomcat.global.request.max").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.threads.busy").gauge().value()).isEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.current").gauge().value()).isGreaterThan(0.0);
    }

    private void checkMbeansAfterRequests() {
        assertThat(registry.get("tomcat.global.sent").functionCounter().count()).isEqualTo(1119.0);
        assertThat(registry.get("tomcat.global.received").functionCounter().count()).isEqualTo(10.0);
        assertThat(registry.get("tomcat.global.error").functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().count()).isEqualTo(2.0);
        assertThat(registry.get("tomcat.global.request").functionTimer().totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.global.request.max").timeGauge().value(TimeUnit.MILLISECONDS)).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.threads.config.max").gauge().value()).isGreaterThan(0.0);
        assertThat(registry.get("tomcat.threads.busy").gauge().value()).isGreaterThanOrEqualTo(0.0);
        assertThat(registry.get("tomcat.threads.current").gauge().value()).isGreaterThan(0.0);
    }
}
