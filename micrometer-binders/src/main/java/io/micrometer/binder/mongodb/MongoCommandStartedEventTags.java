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

import com.mongodb.event.CommandStartedEvent;
import io.micrometer.core.instrument.util.StringUtils;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class MongoCommandStartedEventTags {

    // See https://docs.mongodb.com/manual/reference/command for the command reference
    private static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new HashSet<>(Arrays.asList(
            "aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
            "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
            "dropIndexes", "killCursors", "listIndexes", "reIndex"));
    public static final String UNKNOWN = "unknown";

    public MongoCommandStartedEventTags(CommandStartedEvent event) {
        this.database = event.getDatabaseName();
        this.collection = this.determineCollectionName(event.getCommandName(), event.getCommand())
                .orElse(UNKNOWN);
    }

    private final String collection;
    private final String database;

    public String getDatabase() {
        return database;
    }

    public String getCollection() {
        return collection;
    }

    /**
     * Attempts to determine the name of the collection a command is operating on.
     *
     * <p>Because some commands either do not have collection info or it is problematic to determine the collection info,
     * there is an allow list of command names {@code COMMANDS_WITH_COLLECTION_NAME} used. If {@code commandName} is
     * not in the allow list or there is no collection info in {@code command}, it will use the content of the
     * {@code 'collection'} field on {@code command}, if it exists.
     *
     * <p>Taken from <a href="https://github.com/openzipkin/brave/blob/master/instrumentation/mongodb/src/main/java/brave/mongodb/TraceMongoCommandListener.java#L115">TraceMongoCommandListener.java in Brave</a>
     *
     * @param commandName name of the mongo command
     * @param command     mongo command object
     * @return optional collection name or empty if could not be determined or not in the allow list of command names
     */
    private Optional<String> determineCollectionName(String commandName, BsonDocument command) {
        Optional<String> collectionName = Optional.ofNullable(commandName)
                .filter(COMMANDS_WITH_COLLECTION_NAME::contains)
                .map(command::get)
                .flatMap(this::getNonEmptyBsonString);

        if (collectionName.isPresent()) {
            return collectionName;
        }

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
