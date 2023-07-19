/*
 * Copyright 2020 VMware, Inc.
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

import com.mongodb.event.ConnectionPoolCreatedEvent;
import io.micrometer.core.instrument.Tag;

/**
 * Provides {@link Tag Tags} for Mongo connection pool metrics.
 *
 * @author Gustavo Monarin
 * @since 1.7.0
 */
@FunctionalInterface
public interface MongoConnectionPoolTagsProvider {

    /**
     * Provides tags to be associated with the Mongo connection metrics for the given
     * {@link ConnectionPoolCreatedEvent event}.
     * @param event The Mongo event of when the connection pool is opened
     * @return tags to be associated with metrics recorded for the connection pool
     */
    Iterable<Tag> connectionPoolTags(ConnectionPoolCreatedEvent event);

}
