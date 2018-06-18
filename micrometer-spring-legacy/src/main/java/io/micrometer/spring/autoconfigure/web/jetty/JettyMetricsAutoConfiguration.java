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
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Manabu Matsuzaki
 * @author Jon Schneider
 * @author Michael Weirauch
 */
@Configuration
@AutoConfigureAfter({
        MetricsAutoConfiguration.class, EmbeddedServletContainerAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class })
@ConditionalOnClass(name = {
        "javax.servlet.Servlet", "org.eclipse.jetty.server.Server", "org.eclipse.jetty.util.Loader",
        "org.eclipse.jetty.webapp.WebAppContext" })
@ConditionalOnBean({ JettyEmbeddedServletContainerFactory.class, MeterRegistry.class })
public class JettyMetricsAutoConfiguration {

    @Bean
    public JettyMetricsPostProcessor jettyMetricsPostProcessor(ApplicationContext context) {
        return new JettyMetricsPostProcessor(context);
    }
}
