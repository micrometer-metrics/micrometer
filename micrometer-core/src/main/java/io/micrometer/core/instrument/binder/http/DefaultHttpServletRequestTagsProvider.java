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
package io.micrometer.core.instrument.binder.http;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Default {@link HttpServletRequestTagsProvider}.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
public class DefaultHttpServletRequestTagsProvider implements HttpServletRequestTagsProvider {

    @Override
    public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response) {
        return Tags.of(HttpRequestTags.method(request), HttpRequestTags.status(response),
                HttpRequestTags.outcome(response));
    }

}
