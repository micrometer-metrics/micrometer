/*
 * Copyright 2021 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.binder.mongodb;

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default implementation for {@link MongoCommandTagsProvider}.
 *
 * @author Chris Bono
 * @since 1.7.0
 */
public class DefaultMongoCommandTagsProvider implements MongoCommandTagsProvider {

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(DefaultMongoCommandTagsProvider.class);

    private final ConcurrentMap<Integer, MongoCommandStartedEventTags> inFlightCommandStartedEventTags = new ConcurrentHashMap<>();

    @Override
    public Iterable<Tag> commandTags(CommandEvent event) {
        Optional<MongoCommandStartedEventTags> mongoCommandStartedEventTags = Optional.ofNullable(inFlightCommandStartedEventTags.remove(event.getRequestId()));
        return Tags.of(
                Tag.of("command", event.getCommandName()),
                Tag.of("database", mongoCommandStartedEventTags.map(MongoCommandStartedEventTags::getDatabase).orElse("unknown")),
                Tag.of("collection", mongoCommandStartedEventTags.map(MongoCommandStartedEventTags::getCollection).orElse("unknown")),
                Tag.of("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", event.getConnectionDescription().getServerAddress().toString()),
                Tag.of("status", (event instanceof CommandSucceededEvent) ? "SUCCESS" : "FAILED"));
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);
        addTagsForStartedCommandEvent(event, tags);
    }

    private void addTagsForStartedCommandEvent(CommandEvent event, MongoCommandStartedEventTags tags) {
        if (inFlightCommandStartedEventTags.size() < 1000) {
            inFlightCommandStartedEventTags.put(event.getRequestId(), tags);
            return;
        }
        // Cache over capacity
        WARN_THEN_DEBUG_LOGGER.log("Collection names cache is full - Mongo is not calling listeners properly");
    }
}
