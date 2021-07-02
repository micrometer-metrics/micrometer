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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/**
 * Default implementation for {@link MongoCommandTagsProvider}.
 *
 * @author Chris Bono
 * @since 1.7.0
 */
public class DefaultMongoCommandTagsProvider implements MongoCommandTagsProvider {

    // See https://docs.mongodb.com/manual/reference/command for the command reference
    private static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new HashSet<>(Arrays.asList(
            "aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
            "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
            "dropIndexes", "killCursors", "listIndexes", "reIndex"));

    private static final WarnThenDebugLogger WARN_THEN_DEBUG_LOGGER = new WarnThenDebugLogger(MongoMetricsCommandListener.class);

    private final ConcurrentHashMap<Integer, String> inFlightCommandCollectionNames = new ConcurrentHashMap<>();

    @Override
    public Iterable<Tag> commandTags(CommandEvent event) {
        return Tags.of(
                Tag.of("command", event.getCommandName()),
                Tag.of("collection", getAndRemoveCollectionNameForCommand(event)),
                Tag.of("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", event.getConnectionDescription().getServerAddress().toString()),
                Tag.of("status", (event instanceof CommandSucceededEvent) ? "SUCCESS" : "FAILED"));
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        determineCollectionName(event.getCommandName(), event.getCommand())
                .ifPresent(collectionName -> addCollectionNameForCommand(event, collectionName));
    }

    private void addCollectionNameForCommand(CommandEvent event, String collectionName) {
        if (inFlightCommandCollectionNames.size() < 1000) {
            inFlightCommandCollectionNames.put(event.getRequestId(), collectionName);
            return;
        }
        // Cache over capacity
        WARN_THEN_DEBUG_LOGGER.log("Collection names cache is full - Mongo is not calling listeners properly");
    }

    private String getAndRemoveCollectionNameForCommand(CommandEvent event) {
        String collectionName = inFlightCommandCollectionNames.remove(event.getRequestId());
        return collectionName != null ? collectionName : "unknown";
    }

    /**
     * Attempts to determine the name of the collection a command is operating on.
     *
     * <p>Because some commands either do not have collection info or it is problematic to determine the collection info,
     * there is an allow list of command names {@link #COMMANDS_WITH_COLLECTION_NAME} used. If {@code commandName} is
     * not in the allow list or there is no collection info in {@code command}, it will use the content of the
     * {@code 'collection'} field on {@code command}, if it exists.
     *
     * <p>Taken from <a href="https://github.com/openzipkin/brave/blob/master/instrumentation/mongodb/src/main/java/brave/mongodb/TraceMongoCommandListener.java#L115>TraceMongoCommandListener.java</a>
     *
     * @param commandName name of the mongo command
     * @param command mongo command object
     * @return optional collection name or empty if could not be determined or not in the allow list of command names
     */
    protected Optional<String> determineCollectionName(String commandName, BsonDocument command) {
        if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {
            Optional<String> collectionName = getNonEmptyBsonString(command.get(commandName));
            if (collectionName.isPresent()) {
                return collectionName;
            }
        }
        // Some other commands, like getMore, have a field like {"collection": collectionName}.
        return getNonEmptyBsonString(command.get("collection"));
    }

    /**
     * @return trimmed string from {@code bsonValue} in the Optional or empty Optional if value was not a non-empty string
     */
    private Optional<String> getNonEmptyBsonString(BsonValue bsonValue) {
        return Optional.ofNullable(bsonValue)
                .filter(BsonValue::isString)
                .map(BsonValue::asString)
                .map(BsonString::getValue)
                .map(String::trim)
                .filter(StringUtils::isNotEmpty);
    }
}
