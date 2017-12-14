/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Clint Checketts
 * @author Jon Schneider
 */
class TomcatMetricsTest {
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setup() {
        this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    }

    @Test
    void managerBasedMetrics() throws IOException, ServletException {
        Context context = new StandardContext();

        ManagerBase manager = new ManagerBase() {
            @Override
            public void load() throws ClassNotFoundException, IOException {
            }

            @Override
            public void unload() throws IOException {
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
        } catch (TooManyActiveSessionsException exception) {
            //ignore error, testing rejection
        }

        StandardSession expiredSession = new StandardSession(manager);
        expiredSession.setId("third");
        expiredSession.setCreationTime(System.currentTimeMillis() - 10_000);
        manager.remove(expiredSession, true);

        List<Tag> tags = Tags.zip("metricTag", "val1");
        TomcatMetrics.monitor(registry, manager, tags);

        assertThat(registry.find("tomcat.sessions.active.max").tags(tags).gauge().map(Gauge::value)).hasValue(3.0);
        assertThat(registry.find("tomcat.sessions.active.current").tags(tags).gauge().map(Gauge::value)).hasValue(2.0);
        assertThat(registry.find("tomcat.sessions.expired").tags(tags).functionCounter().map(FunctionCounter::count)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.rejected").tags(tags).functionCounter().map(FunctionCounter::count)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.created").tags(tags).functionCounter().map(FunctionCounter::count)).hasValue(3.0);
        assertThat(registry.find("tomcat.sessions.alive.max").tags(tags).gauge().map(Gauge::value)).isPresent()
            .hasValueSatisfying(val -> assertThat(val).isGreaterThan(1.0));
    }

    @Test
    void mbeansAvailableAfterBinder() throws LifecycleException, InterruptedException {
        TomcatMetrics.monitor(registry, null);

        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if(m.getId().getName().equals("tomcat.global.received"))
                latch.countDown();
        });

        Tomcat server = new Tomcat();
        try {
            StandardHost host = new StandardHost();
            host.setName("localhost");
            server.setHost(host);
            server.setPort(61000);
            server.start();

            latch.await(10, TimeUnit.SECONDS);

            assertThat(registry.find("tomcat.global.received").functionCounter()).isPresent();
        } finally {
            server.stop();
        }
    }

    @Test
    void mbeansAvailableBeforeBinder() throws LifecycleException {
        Tomcat server = new Tomcat();
        try {
            StandardHost host = new StandardHost();
            host.setName("localhost");
            server.setHost(host);
            server.setPort(61000);
            server.start();

            TomcatMetrics.monitor(registry, null);
            assertThat(registry.find("tomcat.global.received").functionCounter()).isPresent();
        } finally {
            server.stop();
        }
    }
}
