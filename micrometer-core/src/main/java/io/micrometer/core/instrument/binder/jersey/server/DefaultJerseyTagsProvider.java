/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.RequestEvent;

/**
 * Default implementation for {@link JerseyTagsProvider}.
 *
 * @author Michael Weirauch
 * @author Johnny Lim
 * @since 1.8.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public final class DefaultJerseyTagsProvider implements JerseyTagsProvider {

    @Override
    public Iterable<Tag> httpRequestTags(RequestEvent event) {
        ContainerResponse response = event.getContainerResponse();
        return Tags.of(JerseyTags.method(event.getContainerRequest()), JerseyTags.uri(event),
                JerseyTags.exception(event), JerseyTags.status(response), JerseyTags.outcome(response));
    }

    @Override
    public Iterable<Tag> httpLongRequestTags(RequestEvent event) {
        return Tags.of(JerseyTags.method(event.getContainerRequest()), JerseyTags.uri(event));
    }

}
