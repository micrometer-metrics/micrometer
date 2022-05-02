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

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;

import static de.flapdoodle.embed.mongo.MongodStarter.getDefaultInstance;

/**
 * Base class for testing MongoDB client based on embedded MongoDB.
 *
 * @author Christophe Bornet
 */
abstract class AbstractMongoDbTest {

    static final String HOST = "localhost";

    int port;

    private MongodExecutable mongodExecutable;

    @BeforeEach
    void startEmbeddedMongoDb() throws IOException {
        MongodStarter starter = getDefaultInstance();

        port = Network.getFreeServerPort();

        MongodConfig mongodConfig = MongodConfig.builder().version(Version.Main.PRODUCTION)
                .net(new Net(HOST, port, Network.localhostIsIPv6())).build();
        mongodExecutable = starter.prepare(mongodConfig);
        mongodExecutable.start();
    }

    @AfterEach
    void stopEmbeddedMongoDb() {
        if (mongodExecutable != null) {
            mongodExecutable.stop();
        }
    }

}
