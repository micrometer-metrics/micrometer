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
package io.micrometer.spring.autoconfigure.web.jetty;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jetty.InstrumentedQueuedThreadPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * @author Manabu Matsuzaki
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(name = "org.eclipse.jetty.server.Server")
@ConditionalOnMissingClass("org.apache.catalina.startup.Tomcat")
public class JettyMetricsConfiguration {
    private final MeterRegistry registry;

    public JettyMetricsConfiguration(MeterRegistry registry) {
        this.registry = registry;
    }

    @Autowired(required = false)
    public void instrumentJettyThreadPool(JettyEmbeddedServletContainerFactory jettyContainerFactory) {
        if (jettyContainerFactory != null) {
            jettyContainerFactory.setThreadPool(new InstrumentedQueuedThreadPool(registry, Collections.emptyList()));
        }
    }
}
