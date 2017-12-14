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
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.session.StandardSession;
import org.apache.catalina.session.TooManyActiveSessionsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TomcatMetricsTest {
    private SimpleMeterRegistry registry;
    private ManagerBase manager;

    @BeforeEach
    void setup() throws SQLException {
        this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        Context context = new StandardContext();

        this.manager = new ManagerBase() {
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
    }

    @Test
    void stats() throws IOException, ServletException {
        manager.createSession("first");
        manager.createSession("second");

        try {
            manager.createSession("forth");
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
        assertThat(registry.find("tomcat.sessions.expired").tags(tags).gauge().map(Gauge::value)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.rejected").tags(tags).gauge().map(Gauge::value)).hasValue(1.0);
        assertThat(registry.find("tomcat.sessions.created").tags(tags).functionCounter().map(FunctionCounter::count)).hasValue(3.0);
        assertThat(registry.find("tomcat.sessions.alive.max").tags(tags).gauge().map(Gauge::value)).isPresent()
            .hasValueSatisfying(val -> assertThat(val).isGreaterThan(1.0));
    }

    @Test
    void jmxMetric() throws Exception {
        MBeanServer server = mock(MBeanServer.class);

        List<Tag> tags = Tags.zip("metricTag", "val1");
        new TomcatMetrics(null, tags, Duration.ofHours(10), () -> server).bindTo(registry);

        assertThat(registry.find("tomcat.threads.busy").gauge()).describedAs("Metric isn't registered until jmx values is detected").isNotPresent();

        //'Register' JMX values
        when(server.queryNames(any(), any())).thenReturn(Collections.singleton(new ObjectName("Tomcat:type=GlobalRequestProcessor,name=fake")));
        when(server.getAttribute(any(), anyString())).thenReturn(32);
        when(server.getAttribute(any(), eq("currentThreadCount"))).thenReturn("badInt");

        //Wait for register thread to register
        Thread.sleep(500);

        //Metric is present and reports a value
        assertThat(registry.find("tomcat.threads.busy").gauge().map(Gauge::value)).hasValue(32.0);
        assertThat(registry.find("tomcat.threads.current").gauge().map(Gauge::value))
            .describedAs("When value is bad, it catches exception and returns 0").hasValue(0.0);
    }
}
