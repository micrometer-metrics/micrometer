/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.transport.http.tags;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.transport.http.HttpRequest;
import io.micrometer.core.instrument.transport.http.HttpResponse;

/**
 * Default implementation of {@link HttpTagsProvider} that can be extended for customization.
 *
 * @author Tommy Ludwig
 * @since 2.0.0
 */
public class DefaultHttpTagsProvider implements HttpTagsProvider {
    @Override
    public Tags getLowCardinalityTags(HttpRequest request, HttpResponse response, Throwable exception) {
        return Tags.of(HttpTags.method(request), HttpTags.uri(request), HttpTags.status(response),
                HttpTags.outcome(response), HttpTags.exception(exception));
    }

    @Override
    public Tags getHighCardinalityTags(HttpRequest request, HttpResponse response, Throwable exception) {
        return Tags.empty();
    }
}
