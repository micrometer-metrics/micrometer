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

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

/**
 * Default implementation for {@link MongoCommandTagsProvider}.
 *
 * @author Chris Bono
 * @since 1.7.0
 */
public class DefaultMongoCommandTagsProvider implements MongoCommandTagsProvider {

    @Override
    public Iterable<Tag> commandTags(CommandEvent event) {
        return Tags.of(Tag.of("command", event.getCommandName()),
                Tag.of("cluster.id",
                        event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue()),
                Tag.of("server.address", event.getConnectionDescription().getServerAddress().toString()),
                Tag.of("status", (event instanceof CommandSucceededEvent) ? "SUCCESS" : "FAILED"));
    }

}
