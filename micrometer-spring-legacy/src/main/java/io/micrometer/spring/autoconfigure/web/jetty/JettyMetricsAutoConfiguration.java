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

import java.util.Collections;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import io.micrometer.spring.autoconfigure.web.jetty.JettyMetricsAutoConfiguration.OnBeansAndOnEmbeddedWebApplicationCondition;
import org.eclipse.jetty.server.Server;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Auto-configuration for Jetty metrics.
 *
 * @author Manabu Matsuzaki
 * @author Jon Schneider
 * @author Michael Weirauch
 * @author Johnny Lim
 * @author Andy Wilkinson
 */
@Configuration
@AutoConfigureAfter({
        MetricsAutoConfiguration.class, EmbeddedServletContainerAutoConfiguration.class,
        SimpleMetricsExportAutoConfiguration.class })
@ConditionalOnClass(name = {
        "javax.servlet.Servlet", "org.eclipse.jetty.server.Server", "org.eclipse.jetty.util.Loader",
        "org.eclipse.jetty.webapp.WebAppContext" })
@Conditional(OnBeansAndOnEmbeddedWebApplicationCondition.class)
public class JettyMetricsAutoConfiguration {

    private volatile Server server;

    @Bean
    public EmbeddedServletContainerCustomizer jettyCustomizer() {
        return (jetty) -> {
            ((JettyEmbeddedServletContainerFactory) jetty).setServerCustomizers(Collections.singleton(this::setServer));
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public JettyServerThreadPoolMetrics jettyThreadPoolMetrics() {
        return new JettyServerThreadPoolMetrics(this.server.getThreadPool(), Collections.emptyList());
    }

    private void setServer(Server server) {
        this.server = server;
    }

    static class OnBeansAndOnEmbeddedWebApplicationCondition extends AllNestedConditions {

        OnBeansAndOnEmbeddedWebApplicationCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(JettyEmbeddedServletContainerFactory.class)
        static class ConditionalOnJettyEmbeddedServletContainerFactoryBean {
        }

        @ConditionalOnBean(MeterRegistry.class)
        static class ConditionalOnMeterRegistryBean {
        }

        @Conditional(OnEmbeddedWebApplicationCondition.class)
        static class ConditionalOnEmbeddedWebApplication {
        }

    }

    static class OnEmbeddedWebApplicationCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            ConditionMessage.Builder message = ConditionMessage.forCondition("Jetty metrics");
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            if (beanFactory.getBeansOfType(AnnotationConfigEmbeddedWebApplicationContext.class).isEmpty()) {
                return ConditionOutcome.noMatch(message.didNotFind("bean of type")
                    .items(ConditionMessage.Style.QUOTE, AnnotationConfigEmbeddedWebApplicationContext.class.getSimpleName()));
            }
            return ConditionOutcome.match(message.found("bean of type")
                .items(ConditionMessage.Style.QUOTE, AnnotationConfigEmbeddedWebApplicationContext.class.getSimpleName()));
        }

    }

}
