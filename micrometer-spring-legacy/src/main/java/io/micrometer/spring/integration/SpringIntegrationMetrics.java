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
package io.micrometer.spring.integration;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.integration.support.management.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class SpringIntegrationMetrics implements MeterBinder, SmartInitializingSingleton {
    private final Iterable<Tag> tags;
    private final IntegrationManagementConfigurer configurer;
    private Collection<MeterRegistry> registries = new ArrayList<>();

    public SpringIntegrationMetrics(IntegrationManagementConfigurer configurer) {
        this(configurer, emptyList());
    }

    public SpringIntegrationMetrics(IntegrationManagementConfigurer configurer, Iterable<Tag> tags) {
        this.configurer = configurer;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("spring.integration.channelNames", configurer, c -> c.getChannelNames().length)
            .tags(tags)
            .description("The number of spring integration channels")
            .register(registry);

        Gauge.builder("spring.integration.handlerNames", configurer, c -> c.getHandlerNames().length)
            .tags(tags)
            .description("The number of spring integration handlers")
            .register(registry);

        Gauge.builder("spring.integration.sourceNames", configurer, c -> c.getSourceNames().length)
            .tags(tags)
            .description("The number of spring integration sources")
            .register(registry);

        registries.add(registry);
    }

    private void addSourceMetrics(MeterRegistry registry) {
        for (String source : configurer.getSourceNames()) {
            MessageSourceMetrics sourceMetrics = configurer.getSourceMetrics(source);
            Iterable<Tag> tagsWithSource = Tags.concat(tags, "source", source);

            FunctionCounter.builder("spring.integration.source.messages", sourceMetrics, MessageSourceMetrics::getMessageCount)
                .tags(tagsWithSource)
                .description("The number of successful handler calls")
                .register(registry);
        }
    }

    private void addHandlerMetrics(MeterRegistry registry) {
        for (String handler : configurer.getHandlerNames()) {
            MessageHandlerMetrics handlerMetrics = configurer.getHandlerMetrics(handler);

            Iterable<Tag> tagsWithHandler = Tags.concat(tags, "handler", handler);

            TimeGauge.builder("spring.integration.handler.duration.max", handlerMetrics, TimeUnit.MILLISECONDS, MessageHandlerMetrics::getMaxDuration)
                .tags(tagsWithHandler)
                .description("The maximum handler duration")
                .register(registry);

            TimeGauge.builder("spring.integration.handler.duration.min", handlerMetrics, TimeUnit.MILLISECONDS, MessageHandlerMetrics::getMinDuration)
                .tags(tagsWithHandler)
                .description("The minimum handler duration")
                .register(registry);

            TimeGauge.builder("spring.integration.handler.duration.mean", handlerMetrics, TimeUnit.MILLISECONDS, MessageHandlerMetrics::getMeanDuration)
                .tags(tagsWithHandler)
                .description("The mean handler duration")
                .register(registry);

            Gauge.builder("spring.integration.handler.activeCount", handlerMetrics, MessageHandlerMetrics::getActiveCount)
                .tags(tagsWithHandler)
                .description("The number of active handlers")
                .register(registry);
        }
    }

    private void addChannelMetrics(MeterRegistry registry) {
        for (String channel : configurer.getChannelNames()) {
            MessageChannelMetrics channelMetrics = configurer.getChannelMetrics(channel);
            Iterable<Tag> tagsWithChannel = Tags.concat(tags, "channel", channel);

            FunctionCounter.builder("spring.integration.channel.sendErrors", channelMetrics, MessageChannelMetrics::getSendErrorCount)
                .tags(tagsWithChannel)
                .description("The number of failed sends (either throwing an exception or rejected by the channel)")
                .register(registry);

            FunctionCounter.builder("spring.integration.channel.sends", channelMetrics, MessageChannelMetrics::getSendCount)
                .tags(tagsWithChannel)
                .description("The number of successful sends")
                .register(registry);

            if (channelMetrics instanceof PollableChannelManagement) {
                FunctionCounter.builder("spring.integration.receives", (PollableChannelManagement) channelMetrics, PollableChannelManagement::getReceiveCount)
                    .tags(tagsWithChannel)
                    .description("The number of messages received")
                    .register(registry);
            }
        }
    }

    @Override
    public void afterSingletonsInstantiated() {
        configurer.afterSingletonsInstantiated();
        registries.forEach(registry -> {
            addChannelMetrics(registry);
            addHandlerMetrics(registry);
            addSourceMetrics(registry);
        });
    }
}
