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

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MongoCommandStartedEventTagsTest {

    private final ConnectionDescription connectionDesc = new ConnectionDescription(
            new ServerId(new ClusterId("cluster1"), new ServerAddress("localhost", 5150)));

    @Test
    void withNameInAllowList() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", new BsonDocument("find", new BsonString(" bar ")));
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);
        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("bar");
    }

    @Test
    void withNameNotInAllowList() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "cmd", new BsonDocument("cmd", new BsonString(" bar ")));
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);
        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("unknown");
    }

    @Test
    void withNameNotInCommand() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", new BsonDocument());
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("unknown");
    }

    @Test
    void withNonStringCommand() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", new BsonDocument("find", BsonBoolean.TRUE));
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("unknown");
    }

    @Test
    void withEmptyStringCommand() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", new BsonDocument("find", new BsonString("  ")));
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("unknown");
    }

    @Test
    void withCollectionFieldOnly() {
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", new BsonDocument("collection", new BsonString(" bar ")));
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("bar");
    }

    @Test
    void withCollectionFieldAndAllowListedCommand() {
        BsonDocument command = new BsonDocument(Arrays.asList(
                new BsonElement("collection", new BsonString("coll")),
                new BsonElement("find", new BsonString("bar"))
        ));
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", command);
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("bar");
    }

    @Test
    void withCollectionFieldAndNotAllowListedCommand() {
        BsonDocument command = new BsonDocument(Arrays.asList(
                new BsonElement("collection", new BsonString("coll")),
                new BsonElement("cmd", new BsonString("bar"))
        ));
        CommandStartedEvent event = new CommandStartedEvent(1000, connectionDesc, "db1", "find", command);
        MongoCommandStartedEventTags tags = new MongoCommandStartedEventTags(event);

        assertThat(tags.getDatabase()).isEqualTo("db1");
        assertThat(tags.getCollection()).isEqualTo("coll");
    }
}
