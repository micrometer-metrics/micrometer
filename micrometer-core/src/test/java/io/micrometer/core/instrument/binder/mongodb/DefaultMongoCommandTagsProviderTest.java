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

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link DefaultMongoCommandTagsProvider}.
 */
class DefaultMongoCommandTagsProviderTest {

    private final ConnectionDescription connectionDesc = new ConnectionDescription(
            new ServerId(new ClusterId("cluster1"), new ServerAddress("localhost", 5150)));

    private DefaultMongoCommandTagsProvider tagsProvider = new DefaultMongoCommandTagsProvider();

    @Test
    void defaultCommandTags() {
        CommandSucceededEvent event = commandSucceededEvent(5150);
        Iterable<Tag>  tags = tagsProvider.commandTags(event);
        assertThat(tags).containsExactlyInAnyOrder(
                Tag.of("command", "find"),
                Tag.of("collection", "unknown"),
                Tag.of("cluster.id", connectionDesc.getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", "localhost:5150"),
                Tag.of("status", "SUCCESS")
        );
    }

    @Test
    void handlesCacheFullGracefully() {
        for (int i = 1; i <= 1000; i++) {
            tagsProvider.commandStarted(commandStartedEvent(i));
        }
        // At this point we have put 1000 into the starting state (and cache) but no more will be put into cache

        // 1001 will not be added to cache and therefore will use 'unknown'
        tagsProvider.commandStarted(commandStartedEvent(1001));
        Iterable<Tag> tags = tagsProvider.commandTags(commandSucceededEvent(1001));
        assertThat(tags).contains(Tag.of("collection", "unknown"));

        // Complete 1000 - which will remove previously added entry from cache
        tags = tagsProvider.commandTags(commandSucceededEvent(1000));
        assertThat(tags).contains(Tag.of("collection", "collection-1000"));

        // 1001 will now be put in cache (since 1000 removed and made room for it)
        tagsProvider.commandStarted(commandStartedEvent(1001));

        // 1002 will not be added to cache and therefore will use 'unknown'
        tagsProvider.commandStarted(commandStartedEvent(1002));

        tags = tagsProvider.commandTags(commandSucceededEvent(1001));
        assertThat(tags).contains(Tag.of("collection", "collection-1001"));

        tags = tagsProvider.commandTags(commandSucceededEvent(1002));
        assertThat(tags).contains(Tag.of("collection", "unknown"));
    }

    private CommandStartedEvent commandStartedEvent(final int requestId) {
        return new CommandStartedEvent(
                requestId,
                connectionDesc,
                "db1",
                "find",
                new BsonDocument("find", new BsonString("collection-" + requestId)));
    }

    private CommandSucceededEvent commandSucceededEvent(final int requestId) {
        return new CommandSucceededEvent(
                requestId,
                connectionDesc,
                "find",
                new BsonDocument(),
                1200L);
    }
}
