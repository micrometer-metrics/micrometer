/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.eclipse.jetty.client.api.Result;

/**
 * Provides {@link Tag Tags} for Jetty {@link org.eclipse.jetty.client.HttpClient} request metrics.
 * Incubating in case there emerges a better way to handle path variable detection.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public interface JettyClientTagsProvider {

    /**
     * Provides tags to be associated with metrics for the given client request.
     *
     * @param result the request result
     * @return tags to associate with metrics recorded for the request
     */
    default Iterable<Tag> httpRequestTags(Result result) {
        return Tags.of(JettyClientTags.method(result.getRequest()),
                JettyClientTags.host(result.getRequest()),
                JettyClientTags.uri(result, this::uriPattern),
                JettyClientTags.exception(result), JettyClientTags.status(result),
                JettyClientTags.outcome(result));
    }

    /**
     * For client metric to be usefully aggregable, we must be able to time everything that goes to a certain
     * endpoint, regardless of the parameters to that endpoint.
     *
     * @param result The result which also contains the original request.
     * @return A URI pattern with path variables and query parameter unsubstituted.
     */
    String uriPattern(Result result);
}
