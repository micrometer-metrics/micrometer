/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.spring.web.tomcat;

import java.util.Collections;

import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Manager;

/**
 * Binds {@link TomcatMetrics} in response to the {@link ApplicationReadyEvent}.
 *
 * @author Andy Wilkinson
 * @since 1.1.0
 */
public class TomcatMetricsBinder implements ApplicationListener<ApplicationReadyEvent> {

    private final MeterRegistry meterRegistry;

    private final Iterable<Tag> tags;

    public TomcatMetricsBinder(MeterRegistry meterRegistry) {
        this(meterRegistry, Collections.emptyList());
    }

    public TomcatMetricsBinder(MeterRegistry meterRegistry, Iterable<Tag> tags) {
        this.meterRegistry = meterRegistry;
        this.tags = tags;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        Manager manager = findManager(applicationContext);
        new TomcatMetrics(manager, this.tags).bindTo(this.meterRegistry);
    }

    private Manager findManager(ApplicationContext applicationContext) {
        if (applicationContext instanceof EmbeddedWebApplicationContext) {
            EmbeddedServletContainer container = ((EmbeddedWebApplicationContext) applicationContext).getEmbeddedServletContainer();
            if (container instanceof TomcatEmbeddedServletContainer) {
                Context context = findContext((TomcatEmbeddedServletContainer) container);
                return context.getManager();
            }
        }
        return null;
    }

    private Context findContext(TomcatEmbeddedServletContainer tomcatWebServer) {
        for (Container container : tomcatWebServer.getTomcat().getHost().findChildren()) {
            if (container instanceof Context) {
                return (Context) container;
            }
        }
        return null;
    }

}
