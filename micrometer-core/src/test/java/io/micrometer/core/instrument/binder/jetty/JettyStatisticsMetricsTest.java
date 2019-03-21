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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JettyStatisticsMetricsTest {
    private SimpleMeterRegistry registry;
    private StatisticsHandler handler;

    @BeforeEach
    void setup() {
        this.registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        this.handler = new StatisticsHandler();
        JettyStatisticsMetrics.monitor(registry, handler);
    }

    @Test
    void stats() throws IOException, ServletException {
        Request baseReq = mock(Request.class);
        HttpChannelState s = new HttpChannelState(null) {
        };
        when(baseReq.getHttpChannelState()).thenReturn(s);
        Response resp = mock(Response.class);
        when(baseReq.getResponse()).thenReturn(resp);
        when(resp.getContentCount()).thenReturn(772L);

        handler.handle("/testUrl", baseReq, new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(registry.get("jetty.requests").functionTimer().count()).isEqualTo(1L);
        assertThat(registry.get("jetty.responses.size").functionCounter().count()).isEqualTo(772.0);
    }
}
