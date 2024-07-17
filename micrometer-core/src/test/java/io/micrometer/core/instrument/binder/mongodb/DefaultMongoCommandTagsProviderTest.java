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
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link DefaultMongoCommandTagsProvider}.
 *
 * @author Chris Bono
 */
class DefaultMongoCommandTagsProviderTest {

    private final ConnectionDescription connectionDesc = new ConnectionDescription(
            new ServerId(new ClusterId("cluster1"), new ServerAddress("localhost", 5150)));

    private final DefaultMongoCommandTagsProvider tagsProvider = new DefaultMongoCommandTagsProvider();

    @Test
    void defaultCommandTags() {
        CommandSucceededEvent event = commandSucceededEvent(5150);
        Iterable<Tag> tags = tagsProvider.commandTags(event);
        assertThat(tags).containsExactlyInAnyOrder(Tag.of("command", "find"), Tag.of("database", "db1"),
                Tag.of("collection", "unknown"),
                Tag.of("cluster.id", connectionDesc.getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", "localhost:5150"), Tag.of("status", "SUCCESS"));
    }

    @Test
    void handlesCommandsOverLimitGracefully() {
        for (int i = 1; i <= 1000; i++) {
            tagsProvider.commandStarted(commandStartedEvent(i));
        }
        // At this point we have started 1000 commands (the state map is full at the time)

        // 1001 will not be added to state map and therefore will use 'unknown'
        tagsProvider.commandStarted(commandStartedEvent(1001));
        Iterable<Tag> tags = tagsProvider.commandTags(commandSucceededEvent(1001));
        assertThat(tags).contains(Tag.of("collection", "unknown"));

        // Complete 1000 - which will remove previously added entry from state map
        tags = tagsProvider.commandTags(commandSucceededEvent(1000));
        assertThat(tags).contains(Tag.of("collection", "collection-1000"));

        // 1001 will now be put in state map (since 1000 removed and made room for it)
        tagsProvider.commandStarted(commandStartedEvent(1001));

        // 1002 will not be added to state map and therefore will use 'unknown'
        tagsProvider.commandStarted(commandStartedEvent(1002));

        tags = tagsProvider.commandTags(commandSucceededEvent(1001));
        assertThat(tags).contains(Tag.of("collection", "collection-1001"));

        tags = tagsProvider.commandTags(commandSucceededEvent(1002));
        assertThat(tags).contains(Tag.of("collection", "unknown"));
    }

    private CommandStartedEvent commandStartedEvent(int requestId) {
        return new CommandStartedEvent(null, -1, requestId, connectionDesc, "db1", "find",
                new BsonDocument("find", new BsonString("collection-" + requestId)));
    }

    private CommandSucceededEvent commandSucceededEvent(int requestId) {
        return new CommandSucceededEvent(null, -1, requestId, connectionDesc, "db1", "find", new BsonDocument(), 1200L);
    }

    @Nested
    class DetermineCollectionName {

        @Test
        void withNameInAllowList() {
            assertThat(tagsProvider.determineCollectionName("find", new BsonDocument("find", new BsonString(" bar "))))
                .hasValue("bar");
        }

        @Test
        void withNameNotInAllowList() {
            assertThat(tagsProvider.determineCollectionName("cmd", new BsonDocument("cmd", new BsonString(" bar "))))
                .isEmpty();
        }

        @Test
        void withNameNotInCommand() {
            assertThat(tagsProvider.determineCollectionName("find", new BsonDocument())).isEmpty();
        }

        @Test
        void withNonStringCommand() {
            assertThat(tagsProvider.determineCollectionName("find", new BsonDocument("find", BsonBoolean.TRUE)))
                .isEmpty();
        }

        @Test
        void withEmptyStringCommand() {
            assertThat(tagsProvider.determineCollectionName("find", new BsonDocument("find", new BsonString("  "))))
                .isEmpty();
        }

        @Test
        void withCollectionFieldOnly() {
            assertThat(tagsProvider.determineCollectionName("find",
                    new BsonDocument("collection", new BsonString(" bar "))))
                .hasValue("bar");
        }

        @Test
        void withCollectionFieldAndAllowListedCommand() {
            BsonDocument command = new BsonDocument(Arrays.asList(new BsonElement("collection", new BsonString("coll")),
                    new BsonElement("find", new BsonString("bar"))));
            assertThat(tagsProvider.determineCollectionName("find", command)).hasValue("bar");
        }

        @Test
        void withCollectionFieldAndNotAllowListedCommand() {
            BsonDocument command = new BsonDocument(Arrays.asList(new BsonElement("collection", new BsonString("coll")),
                    new BsonElement("cmd", new BsonString("bar"))));
            assertThat(tagsProvider.determineCollectionName("find", command)).hasValue("coll");
        }

    }

}
