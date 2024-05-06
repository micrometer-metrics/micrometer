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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.http.Outcome;
import org.eclipse.jetty.server.Request;

/**
 * Default {@link JettyCoreRequestTagsProvider}.
 *
 * @author Joakim Erdfelt
 * @since 1.13.0
 */
public class DefaultJettyCoreRequestTagsProvider implements JettyCoreRequestTagsProvider {

    private static final Tag STATUS_UNKNOWN = Tag.of("status", "UNKNOWN");

    private static final Tag METHOD_UNKNOWN = Tag.of("method", "UNKNOWN");

    @Override
    public Iterable<Tag> getTags(Request request) {
        return Tags.of(method(request), status(request), outcome(request));
    }

    private Tag method(Request request) {
        return (request != null) ? Tag.of("method", request.getMethod()) : METHOD_UNKNOWN;
    }

    private Tag status(Request request) {
        if (request == null)
            return STATUS_UNKNOWN;

        Object status = request.getAttribute(TimedHandler.RESPONSE_STATUS_ATTRIBUTE);
        if (status instanceof Integer statusInt)
            return Tag.of("status", Integer.toString(statusInt));
        return STATUS_UNKNOWN;
    }

    private Tag outcome(Request request) {
        Outcome outcome = Outcome.UNKNOWN;
        if (request != null) {
            Object status = request.getAttribute(TimedHandler.RESPONSE_STATUS_ATTRIBUTE);
            if (status instanceof Integer statusInt)
                outcome = Outcome.forStatus(statusInt);
        }
        return outcome.asTag();
    }

}
