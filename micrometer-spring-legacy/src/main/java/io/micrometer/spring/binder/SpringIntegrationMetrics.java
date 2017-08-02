/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.spring.binder;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.support.management.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Jon Schneider
 */
public class SpringIntegrationMetrics implements MeterBinder, SmartInitializingSingleton {
    private Collection<MeterRegistry> registries = new ArrayList<>();

    private final IntegrationManagementConfigurer configurer;
    public SpringIntegrationMetrics(IntegrationManagementConfigurer configurer) {
        this.configurer = configurer;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("spring_integration_channelNames", configurer, c -> c.getChannelNames().length);
        registry.gauge("spring_integration_handlerNames", configurer, c -> c.getHandlerNames().length);
        registry.gauge("spring_integration_sourceNames", configurer, c -> c.getSourceNames().length);
        registries.add(registry);
    }

    private void addSourceMetrics(MeterRegistry registry) {
        for (String source : configurer.getSourceNames()) {
            MessageSourceMetrics sourceMetrics = configurer.getSourceMetrics(source);
            List<Tag> tags = Collections.singletonList(Tag.of("source", source));
            registry.counter("spring_integration_source_messages", tags, sourceMetrics, MessageSourceMetrics::getMessageCount);
        }
    }

    private void addHandlerMetrics(MeterRegistry registry) {
        for (String handler : configurer.getHandlerNames()) {
            MessageHandlerMetrics handlerMetrics = configurer.getHandlerMetrics(handler);

            // TODO could use improvement to dynamically commute the handler name with its ID, which can change after
            // creation as shown in the SpringIntegrationApplication sample.
            List<Tag> tags = Collections.singletonList(Tag.of("handler", handler));

            registry.gauge("spring_integration_handler_duration_max", tags, handlerMetrics, MessageHandlerMetrics::getMaxDuration);
            registry.gauge("spring_integration_handler_duration_min", tags, handlerMetrics, MessageHandlerMetrics::getMinDuration);
            registry.gauge("spring_integration_handler_duration_mean", tags, handlerMetrics, MessageHandlerMetrics::getMeanDuration);

            registry.gauge("spring_integration_handler_activeCount", tags, handlerMetrics, MessageHandlerMetrics::getActiveCount);
        }
    }

    private void addChannelMetrics(MeterRegistry registry) {
        for (String channel : configurer.getChannelNames()) {
            MessageChannelMetrics channelMetrics = configurer.getChannelMetrics(channel);
            List<Tag> tags = Collections.singletonList(Tag.of("channel", channel));

            registry.counter("spring_integration_channel_sendErrors", tags, channelMetrics, MessageChannelMetrics::getSendErrorCount);
            registry.counter("spring_integration_channel_sends", tags, channelMetrics, MessageChannelMetrics::getSendCount);

            if (channelMetrics instanceof PollableChannelManagement) {
                registry.counter("spring_integration_receives", tags, (PollableChannelManagement) channelMetrics,
                        PollableChannelManagement::getReceiveCount);
            }
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        // TODO better would be to use a BeanPostProcessor
        configurer.afterSingletonsInstantiated();
        registries.forEach(registry -> {
            addChannelMetrics(registry);
            addHandlerMetrics(registry);
            addSourceMetrics(registry);
        });
    }
}
