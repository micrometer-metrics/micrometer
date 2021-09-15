/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.event.*;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.concurrent.TimeUnit;

/**
 * {@link CommandListener} for collecting command metrics from {@link MongoClient}.
 *
 * @author Christophe Bornet
 * @author Chris Bono
 * @since 1.2.0
 */
@NonNullApi
@NonNullFields
@Incubating(since = "1.2.0")
public class MongoMetricsCommandListener implements CommandListener {

    private final MeterRegistry registry;

    private final MongoCommandTagsProvider tagsProvider;

    /**
     * Constructs a command listener that uses the default tags provider.
     *
     * @param registry meter registry
     */
    public MongoMetricsCommandListener(MeterRegistry registry) {
        this(registry, new DefaultMongoCommandTagsProvider());
    }

    /**
     * Constructs a command listener with a custom tags provider.
     *
     * @param registry meter registry
     * @param tagsProvider provides tags to be associated with metrics for the given Mongo command
     * @since 1.7.0
     */
    public MongoMetricsCommandListener(MeterRegistry registry, MongoCommandTagsProvider tagsProvider) {
        this.registry = registry;
        this.tagsProvider = tagsProvider;
    }

    @Override
    public void commandStarted(CommandStartedEvent commandStartedEvent) {
        tagsProvider.commandStarted(commandStartedEvent);
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        timeCommand(event, event.getElapsedTime(TimeUnit.NANOSECONDS));
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        timeCommand(event, event.getElapsedTime(TimeUnit.NANOSECONDS));
    }

    private void timeCommand(CommandEvent event, long elapsedTimeInNanoseconds) {
        Timer.builder("mongodb.driver.commands")
                .description("Timer of mongodb commands")
                .tags(tagsProvider.commandTags(event))
                .register(registry)
                .record(elapsedTimeInNanoseconds, TimeUnit.NANOSECONDS);
    }

}
