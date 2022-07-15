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

import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ConnectionId;
import com.mongodb.event.CommandStartedEvent;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.binder.mongodb.MongoObservation.HighCardinalityCommandKeyNames;
import io.micrometer.core.instrument.binder.mongodb.MongoObservation.LowCardinalityCommandKeyNames;

/**
 * Default {@link MongoHandlerObservationConvention} implementation.
 *
 * @author Greg Turnquist
 * @since 1.10.0
 */
public class DefaultMongoHandlerObservationConvention implements MongoHandlerObservationConvention {

    @Override
    public KeyValues getLowCardinalityKeyValues(MongoHandlerContext context) {

        KeyValues keyValues = KeyValues.empty();

        if (context.getCollectionName() != null) {
            keyValues = keyValues.and(KeyValue.of(LowCardinalityCommandKeyNames.MONGODB_COLLECTION.getKeyName(),
                    context.getCollectionName()));
        }

        KeyValue connectionTag = connectionTag(context.getCommandStartedEvent());
        if (connectionTag != null) {
            keyValues = keyValues.and(connectionTag);
        }

        return keyValues;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(MongoHandlerContext context) {

        return KeyValues.of(KeyValue.of(HighCardinalityCommandKeyNames.MONGODB_COMMAND.getKeyName(),
                context.getCommandStartedEvent().getCommandName()));
    }

    /**
     * Extract connection details for a MongoDB connection into a {@link KeyValue}.
     * @param event
     * @return
     */
    private static KeyValue connectionTag(CommandStartedEvent event) {

        ConnectionDescription connectionDescription = event.getConnectionDescription();

        if (connectionDescription != null) {

            ConnectionId connectionId = connectionDescription.getConnectionId();
            if (connectionId != null) {
                return KeyValue.of(LowCardinalityCommandKeyNames.MONGODB_CLUSTER_ID.getKeyName(),
                        connectionId.getServerId().getClusterId().getValue());
            }
        }

        return null;
    }

    @Override
    public String getName() {
        return "mongodb.command";
    }

}
