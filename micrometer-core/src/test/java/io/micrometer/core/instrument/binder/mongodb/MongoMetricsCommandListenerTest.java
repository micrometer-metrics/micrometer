/*
 * Copyright 2019 VMware, Inc.
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

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.ClusterListener;
import com.mongodb.event.ClusterOpeningEvent;
import com.mongodb.event.CommandEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for {@link MongoMetricsCommandListener}.
 *
 * @author Christophe Bornet
 * @author Chris Bono
 */
class MongoMetricsCommandListenerTest extends AbstractMongoDbTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @Nullable private MongoClient mongo;

    @Test
    void shouldCreateSuccessCommandMetric() {
        AtomicReference<String> clusterIdRef = new AtomicReference<>();
        mongo = createMongoClient(clusterIdRef);

        assertThat(mongo).isNotNull();
        String clusterId = await().untilAtomic(clusterIdRef, notNullValue());

        // tag::example[]
        mongo.getDatabase("test").getCollection("testCol").insertOne(new Document("testDoc", new Date()));

        Tags tags = Tags.of("cluster.id", clusterId, "server.address", String.format("%s:%s", host, port), "command",
                "insert", "database", "test", "collection", "testCol", "status", "SUCCESS");
        assertThat(registry.get("mongodb.driver.commands").tags(tags).timer().count()).isEqualTo(1);
        // end::example[]
    }

    @Test
    void shouldCreateFailedCommandMetric() {
        AtomicReference<String> clusterIdRef = new AtomicReference<>();
        mongo = createMongoClient(clusterIdRef);

        assertThat(mongo).isNotNull();
        String clusterId = await().untilAtomic(clusterIdRef, notNullValue());

        mongo.getDatabase("test").getCollection("testCol").dropIndex("nonExistentIndex");

        Tags tags = Tags.of("cluster.id", clusterId, "server.address", String.format("%s:%s", host, port), "command",
                "dropIndexes", "database", "test", "collection", "testCol", "status", "FAILED");
        assertThat(registry.get("mongodb.driver.commands").tags(tags).timer().count()).isEqualTo(1);
    }

    @Test
    void shouldCreateSuccessCommandMetricWithCustomSettings() {
        AtomicReference<String> clusterIdRef = new AtomicReference<>();

        MongoCommandTagsProvider tagsProvider = new DefaultMongoCommandTagsProvider() {
            @Override
            public Iterable<Tag> commandTags(CommandEvent event) {
                return Tags.of(super.commandTags(event)).and(Tag.of("mongoz", "5150"));
            }
        };
        MongoClientSettings settings = MongoClientSettings.builder()
            .addCommandListener(new MongoMetricsCommandListener(registry, tagsProvider))
            .applyToClusterSettings(builder -> builder.hosts(singletonList(new ServerAddress(host, port)))
                .addClusterListener(new ClusterListener() {
                    @Override
                    public void clusterOpening(ClusterOpeningEvent event) {
                        clusterIdRef.set(event.getClusterId().getValue());
                    }
                }))
            .build();

        mongo = MongoClients.create(settings);
        assertThat(mongo).isNotNull();
        String clusterId = await().untilAtomic(clusterIdRef, notNullValue());

        mongo.getDatabase("test").getCollection("testCol").insertOne(new Document("testDoc", new Date()));
        Tags tags = Tags.of("cluster.id", clusterId, "server.address", String.format("%s:%s", host, port), "command",
                "insert", "database", "test", "collection", "testCol", "status", "SUCCESS", "mongoz", "5150");
        assertThat(registry.get("mongodb.driver.commands").tags(tags).timer().count()).isEqualTo(1);
    }

    @Test
    void shouldCreateFailedCommandMetricWithCustomSettings() {
        AtomicReference<String> clusterIdRef = new AtomicReference<>();

        MongoCommandTagsProvider tagsProvider = new DefaultMongoCommandTagsProvider() {
            @Override
            public Iterable<Tag> commandTags(CommandEvent event) {
                return Tags.of(super.commandTags(event)).and(Tag.of("mongoz", "5150"));
            }
        };
        MongoClientSettings settings = MongoClientSettings.builder()
            .addCommandListener(new MongoMetricsCommandListener(registry, tagsProvider))
            .applyToClusterSettings(builder -> builder.hosts(singletonList(new ServerAddress(host, port)))
                .addClusterListener(new ClusterListener() {
                    @Override
                    public void clusterOpening(ClusterOpeningEvent event) {
                        clusterIdRef.set(event.getClusterId().getValue());
                    }
                }))
            .build();

        mongo = MongoClients.create(settings);
        assertThat(mongo).isNotNull();
        String clusterId = await().untilAtomic(clusterIdRef, notNullValue());

        mongo.getDatabase("test").getCollection("testCol").dropIndex("nonExistentIndex");
        Tags tags = Tags.of("cluster.id", clusterId, "server.address", String.format("%s:%s", host, port), "command",
                "dropIndexes", "database", "test", "collection", "testCol", "status", "FAILED", "mongoz", "5150");
        assertThat(registry.get("mongodb.driver.commands").tags(tags).timer().count()).isEqualTo(1);
    }

    @Test
    void shouldSupportConcurrentCommands() throws InterruptedException {
        mongo = createMongoClient(new AtomicReference<>());
        assertThat(mongo).isNotNull();

        for (int i = 0; i < 20; i++) {
            Map<String, Thread> commandThreadMap = new HashMap<>();

            commandThreadMap.put("insert",
                    new Thread(() -> requireNonNull(mongo).getDatabase("test")
                        .getCollection("testCol")
                        .insertOne(new Document("testField", new Date()))));

            commandThreadMap.put("update",
                    new Thread(() -> requireNonNull(mongo).getDatabase("test")
                        .getCollection("testCol")
                        .updateOne(new Document("nonExistentField", "abc"),
                                new Document("$set", new Document("nonExistentField", "xyz")))));

            commandThreadMap.put("delete",
                    new Thread(() -> requireNonNull(mongo).getDatabase("test")
                        .getCollection("testCol")
                        .deleteOne(new Document("nonExistentField", "abc"))));

            commandThreadMap.put("aggregate", new Thread(
                    () -> requireNonNull(mongo).getDatabase("test").getCollection("testCol").countDocuments()));

            for (Thread thread : commandThreadMap.values()) {
                thread.start();
            }

            for (Thread thread : commandThreadMap.values()) {
                thread.join();
            }

            final int iterationsCompleted = i + 1;

            for (String command : commandThreadMap.keySet()) {
                long commandsRecorded;
                try {
                    commandsRecorded = registry.get("mongodb.driver.commands")
                        .tags(Tags.of("command", command))
                        .timer()
                        .count();
                }
                catch (MeterNotFoundException e) {
                    commandsRecorded = 0L;
                }

                assertThat(commandsRecorded)
                    .as("Check how many %s commands were recorded after %d iterations", command, iterationsCompleted)
                    .isEqualTo(iterationsCompleted);
            }
        }
    }

    @AfterEach
    void destroy() {
        if (mongo != null) {
            mongo.close();
        }
    }

    private MongoClient createMongoClient(AtomicReference<String> clusterId) {
        // tag::setup_1[]
        MongoClientSettings settings = MongoClientSettings.builder()
            .addCommandListener(new MongoMetricsCommandListener(registry))
        // end::setup_1[]
            .applyToClusterSettings(builder -> builder.hosts(singletonList(new ServerAddress(host, port)))
                .addClusterListener(new ClusterListener() {
                    @Override
                    public void clusterOpening(ClusterOpeningEvent event) {
                        clusterId.set(event.getClusterId().getValue());
                    }
                }))
        // tag::setup_2[]
            .build();

        MongoClient mongo = MongoClients.create(settings);
        // end::setup_2[]
        return mongo;
    }

}
