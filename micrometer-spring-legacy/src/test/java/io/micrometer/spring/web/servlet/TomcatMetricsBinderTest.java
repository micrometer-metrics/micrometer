/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.web.servlet;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.web.tomcat.TomcatMetricsBinder;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TomcatMetricsBinder}.
 *
 * @author Oleksii Bondar
 */
public class TomcatMetricsBinderTest {

    @Test
    public void reportTomcatMetricsWithOutTags() {
        ApplicationReadyEvent event = setupMocks();

        MeterRegistry meterRegistry = bindToMeterRegistry(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNotNull();
    }

    @Test
    public void reportTomcatMetricsWithExpectedTags() {
        ApplicationReadyEvent event = setupMocks();

        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        ImmutableTag expectedTag = new ImmutableTag("version", "1");
        List<Tag> tags = Arrays.asList(expectedTag);
        TomcatMetricsBinder metricsBinder = new TomcatMetricsBinder(meterRegistry, tags);
        metricsBinder.onApplicationEvent(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNotNull();
    }

    @Test
    public void doNotReportMetricsForNonWebServerApplicationContext() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        GenericApplicationContext context = mock(GenericApplicationContext.class);
        given(event.getApplicationContext()).willReturn(context);

        MeterRegistry meterRegistry = bindToMeterRegistry(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNull();
    }

    @Test
    public void doNotReportMetricsForNonTomcatWebServer() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        EmbeddedWebApplicationContext context = mock(EmbeddedWebApplicationContext.class);
        given(event.getApplicationContext()).willReturn(context);
        JettyEmbeddedServletContainer webServer = mock(JettyEmbeddedServletContainer.class);
        given(context.getEmbeddedServletContainer()).willReturn(webServer);

        MeterRegistry meterRegistry = bindToMeterRegistry(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNull();
    }

    @Test
    public void doNotReportMetricsForEmptyTomcatContainer() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        EmbeddedWebApplicationContext context = mock(EmbeddedWebApplicationContext.class);
        given(event.getApplicationContext()).willReturn(context);
        TomcatEmbeddedServletContainer webServer = mock(TomcatEmbeddedServletContainer.class);
        given(context.getEmbeddedServletContainer()).willReturn(webServer);
        Tomcat tomcat = mock(Tomcat.class);
        given(webServer.getTomcat()).willReturn(tomcat);
        Host host = mock(Host.class);
        given(tomcat.getHost()).willReturn(host);
        given(host.findChildren()).willReturn(new Container[0]);

        MeterRegistry meterRegistry = bindToMeterRegistry(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNull();
    }

    @Test
    public void doNotReportMetricsForNonCatalinaContext() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        EmbeddedWebApplicationContext context = mock(EmbeddedWebApplicationContext.class);
        given(event.getApplicationContext()).willReturn(context);
        TomcatEmbeddedServletContainer webServer = mock(TomcatEmbeddedServletContainer.class);
        given(context.getEmbeddedServletContainer()).willReturn(webServer);
        Tomcat tomcat = mock(Tomcat.class);
        given(webServer.getTomcat()).willReturn(tomcat);
        Host host = mock(Host.class);
        given(tomcat.getHost()).willReturn(host);
        Engine engine = mock(Engine.class);
        given(host.findChildren()).willReturn(new Container[] {engine});

        MeterRegistry meterRegistry = bindToMeterRegistry(event);

        assertThat(fetchExpectedMetric(meterRegistry)).isNull();
    }

    private ApplicationReadyEvent setupMocks() {
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        EmbeddedWebApplicationContext context = mock(EmbeddedWebApplicationContext.class);
        given(event.getApplicationContext()).willReturn(context);
        TomcatEmbeddedServletContainer webServer = mock(TomcatEmbeddedServletContainer.class);
        given(context.getEmbeddedServletContainer()).willReturn(webServer);
        Tomcat tomcat = mock(Tomcat.class);
        given(webServer.getTomcat()).willReturn(tomcat);
        Host host = mock(Host.class);
        given(tomcat.getHost()).willReturn(host);
        Context container = mock(Context.class);
        given(host.findChildren()).willReturn(new Container[] {container});
        Manager manager = mock(Manager.class);
        given(container.getManager()).willReturn(manager);
        return event;
    }

    private MeterRegistry bindToMeterRegistry(ApplicationReadyEvent event) {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        TomcatMetricsBinder metricsBinder = new TomcatMetricsBinder(meterRegistry);
        metricsBinder.onApplicationEvent(event);
        return meterRegistry;
    }

    private Gauge fetchExpectedMetric(MeterRegistry meterRegistry) {
        return meterRegistry.find("tomcat.sessions.active.max").gauge();
    }

}
