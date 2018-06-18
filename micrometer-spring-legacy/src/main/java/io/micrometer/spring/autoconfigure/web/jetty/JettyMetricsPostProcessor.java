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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jetty.InstrumentedQueuedThreadPool;

/**
 * @author Michael Weirauch
 */
public class JettyMetricsPostProcessor implements BeanPostProcessor, Ordered {
    private final ApplicationContext context;
    private volatile MeterRegistry registry;

    JettyMetricsPostProcessor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JettyEmbeddedServletContainerFactory) {
            ((JettyEmbeddedServletContainerFactory) bean).setThreadPool(
                    new InstrumentedQueuedThreadPool(getMeterRegistry(), Collections.emptyList()));
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private MeterRegistry getMeterRegistry() {
        if (this.registry == null) {
            this.registry = this.context.getBean(MeterRegistry.class);
        }
        return this.registry;
    }
}
