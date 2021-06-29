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

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonElement;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MongoCommandUtil}.
 */
class MongoCommandUtilTest {

    private final MongoCommandUtil commandUtil = new MongoCommandUtil();
    
    @Test
    void determineCollectionNameWithNameInAllowList() {
        assertThat(commandUtil.determineCollectionName("find", new BsonDocument("find", new BsonString(" bar ")))).hasValue("bar");
    }

    @Test
    void determineCollectionNameWithNameNotInAllowList() {
        assertThat(commandUtil.determineCollectionName("cmd", new BsonDocument("cmd", new BsonString(" bar ")))).isEmpty();
    }

    @Test
    void determineCollectionNameWithNameNotInCommand() {
        assertThat(commandUtil.determineCollectionName("find", new BsonDocument())).isEmpty();
    }

    @Test
    void determineCollectionNameWithNonStringCommand() {
        assertThat(commandUtil.determineCollectionName("find", new BsonDocument("find", BsonBoolean.TRUE))).isEmpty();
    }

    @Test
    void determineCollectionNameWithEmptyStringCommand() {
        assertThat(commandUtil.determineCollectionName("find", new BsonDocument("find", new BsonString("  ")))).isEmpty();
    }

    @Test
    void determineCollectionNameWithCollectionFieldOnly() {
        assertThat(commandUtil.determineCollectionName("find", new BsonDocument("collection", new BsonString(" bar ")))).hasValue("bar");
    }

    @Test
    void determineCollectionNameWithCollectionFieldAndAllowListedCommand() {
        BsonDocument command = new BsonDocument(Arrays.asList(
                new BsonElement("collection", new BsonString("coll")),
                new BsonElement("find", new BsonString("bar"))
        ));
        assertThat(commandUtil.determineCollectionName("find", command)).hasValue("bar");
    }

    @Test
    void determineCollectionNameWithCollectionFieldAndNotAllowListedCommand() {
        BsonDocument command = new BsonDocument(Arrays.asList(
                new BsonElement("collection", new BsonString("coll")),
                new BsonElement("cmd", new BsonString("bar"))
        ));
        assertThat(commandUtil.determineCollectionName("find", command)).hasValue("coll");
    }
}
