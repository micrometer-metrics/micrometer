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

import com.mongodb.event.CommandEvent;
import com.mongodb.event.CommandStartedEvent;
import io.micrometer.core.instrument.Tag;

/**
 * Provides {@link Tag Tags} for Mongo command metrics.
 *
 * @author Chris Bono
 * @since 1.7.0
 */
@FunctionalInterface
public interface MongoCommandTagsProvider {

    /**
     * Signals that a command has started and is a chance for implementations to prepare
     * or do any necessary pre-processing.
     *
     * @param commandStartedEvent event representing the issued command
     */
    default void commandStarted(CommandStartedEvent commandStartedEvent) {
    }

    /**
     * Provides tags to be associated with metrics for the given Mongo command.
     *
     * @param commandEvent event representing the issued command
     * @return tags to associate with metrics recorded for the command
     */
    Iterable<Tag> commandTags(CommandEvent commandEvent);
}
