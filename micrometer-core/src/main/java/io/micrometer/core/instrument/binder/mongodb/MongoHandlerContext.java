/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.RequestContext;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link Observation.Context} that contains MongoDB events.
 *
 * @author Marcin Grzejszczak
 * @author Greg Turnquist
 * @since 1.10.0
 */
public class MongoHandlerContext extends Observation.Context {

    /**
     * @see https://docs.mongodb.com/manual/reference/command for the command reference
     */
    private static final Set<String> COMMANDS_WITH_COLLECTION_NAME = new LinkedHashSet<>(
            Arrays.asList("aggregate", "count", "distinct", "mapReduce", "geoSearch", "delete", "find", "findAndModify",
                    "insert", "update", "collMod", "compact", "convertToCapped", "create", "createIndexes", "drop",
                    "dropIndexes", "killCursors", "listIndexes", "reIndex"));

    private final CommandStartedEvent commandStartedEvent;

    private final RequestContext requestContext;

    private final String collectionName;

    private CommandSucceededEvent commandSucceededEvent;

    private CommandFailedEvent commandFailedEvent;

    public MongoHandlerContext(CommandStartedEvent commandStartedEvent, RequestContext requestContext) {

        this.commandStartedEvent = commandStartedEvent;
        this.requestContext = requestContext;
        this.collectionName = getCollectionName(commandStartedEvent);
    }

    public CommandStartedEvent getCommandStartedEvent() {
        return this.commandStartedEvent;
    }

    public RequestContext getRequestContext() {
        return this.requestContext;
    }

    public String getCollectionName() {
        return this.collectionName;
    }

    public String getContextualName() {

        if (this.collectionName == null) {
            return this.commandStartedEvent.getCommandName();
        }

        return this.commandStartedEvent.getCommandName() + " " + this.collectionName;
    }

    public void setCommandSucceededEvent(CommandSucceededEvent commandSucceededEvent) {
        this.commandSucceededEvent = commandSucceededEvent;
    }

    public void setCommandFailedEvent(CommandFailedEvent commandFailedEvent) {
        this.commandFailedEvent = commandFailedEvent;
    }

    /**
     * Transform the command name into a collection name;
     * @param event the {@link CommandStartedEvent}
     * @return the name of the collection based on the command
     */
    @Nullable
    private static String getCollectionName(CommandStartedEvent event) {

        String commandName = event.getCommandName();
        BsonDocument command = event.getCommand();

        if (COMMANDS_WITH_COLLECTION_NAME.contains(commandName)) {

            String collectionName = getNonEmptyBsonString(command.get(commandName));

            if (collectionName != null) {
                return collectionName;
            }
        }

        // Some other commands, like getMore, have a field like {"collection":
        // collectionName}.
        return getNonEmptyBsonString(command.get("collection"));
    }

    /**
     * Utility method to convert {@link BsonValue} into a plain string.
     * @return trimmed string from {@code bsonValue} or null if the trimmed string was
     * empty or the value wasn't a string
     */
    @Nullable
    private static String getNonEmptyBsonString(BsonValue bsonValue) {

        if (bsonValue == null || !bsonValue.isString()) {
            return null;
        }

        String stringValue = bsonValue.asString().getValue().trim();

        return stringValue.isEmpty() ? null : stringValue;
    }

}
