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
package io.micrometer.spring.web.jetty;

import java.util.Collections;

import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import org.eclipse.jetty.util.thread.ThreadPool;

/**
 * Binds {@link JettyServerThreadPoolMetrics} in response to the
 * {@link ApplicationReadyEvent}.
 *
 * @author Andy Wilkinson
 * @since 1.1.0
 */
public class JettyServerThreadPoolMetricsBinder
        implements ApplicationListener<ApplicationReadyEvent> {

    private final MeterRegistry meterRegistry;

    private final Iterable<Tag> tags;

    public JettyServerThreadPoolMetricsBinder(MeterRegistry meterRegistry) {
        this(meterRegistry, Collections.emptyList());
    }

    public JettyServerThreadPoolMetricsBinder(MeterRegistry meterRegistry, Iterable<Tag> tags) {
        this.meterRegistry = meterRegistry;
        this.tags = tags;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        ThreadPool threadPool = findThreadPool(applicationContext);
        if (threadPool != null) {
            new JettyServerThreadPoolMetrics(threadPool, this.tags)
                .bindTo(this.meterRegistry);
        }
    }

    private ThreadPool findThreadPool(ApplicationContext applicationContext) {
        if (applicationContext instanceof EmbeddedWebApplicationContext) {
            EmbeddedServletContainer container = ((EmbeddedWebApplicationContext) applicationContext).getEmbeddedServletContainer();
            if (container instanceof JettyEmbeddedServletContainer) {
                return ((JettyEmbeddedServletContainer) container).getServer().getThreadPool();
            }
        }
        return null;
    }

}
