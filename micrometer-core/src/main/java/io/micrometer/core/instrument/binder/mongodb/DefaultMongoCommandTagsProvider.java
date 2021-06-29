/**
 * Copyright 2021 VMware, Inc.
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

import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;

/**
 * Default implementation for {@link MongoCommandTagsProvider}.
 *
 * @author Chris Bono
 * @since 1.7.0
 */
public class DefaultMongoCommandTagsProvider implements MongoCommandTagsProvider {

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(MongoMetricsCommandListener.class);

    // The following is all related to extracting collection name
    private final MongoCommandUtil mongoCommandUtil = new MongoCommandUtil();

    private final ConcurrentHashMap<Integer, String> collectionNamesCache = new ConcurrentHashMap<>();

    @Override
    public Iterable<Tag> commandTags(CommandEvent event) {
        return Tags.of(
                Tag.of("command", event.getCommandName()),
                Tag.of("collection", removeCollectionNameFromCache(event)),
                Tag.of("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", event.getConnectionDescription().getServerAddress().toString()),
                Tag.of("status", (event instanceof CommandSucceededEvent) ? "SUCCESS" : "FAILED"));
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        mongoCommandUtil.determineCollectionName(event.getCommandName(), event.getCommand())
                .ifPresent(collectionName -> addCollectionNameToCache(event, collectionName));
    }

    private void addCollectionNameToCache(CommandEvent event, String collectionName) {
        if (collectionNamesCache.size() < 1000) {
            collectionNamesCache.put(event.getRequestId(), collectionName);
            return;
        }
        // Cache over capacity
        WARN_THEN_DEBUG_LOGGER.log("Collection names cache is full - Mongo is not calling listeners properly");
    }

    private String removeCollectionNameFromCache(CommandEvent event) {
        String collectionName = collectionNamesCache.remove(event.getRequestId());
        return collectionName != null ? collectionName : "unknown";
    }
}
