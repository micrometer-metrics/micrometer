/*
 * Copyright 2024 VMware, Inc.
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

package io.micrometer.jetty12.server;

import io.micrometer.core.instrument.Tag;
import org.eclipse.jetty.server.Request;

/**
 * Provides {@link Tag Tags} for Jetty Core request handling.
 *
 * @author Joakim Erdfelt
 * @since 1.13.0
 */
@FunctionalInterface
public interface JettyCoreRequestTagsProvider {

    /**
     * Provides tags to be associated with metrics for the given {@code request}.
     * @param request the request
     * @return tags to associate with metrics for the request
     */
    Iterable<Tag> getTags(Request request);

}
