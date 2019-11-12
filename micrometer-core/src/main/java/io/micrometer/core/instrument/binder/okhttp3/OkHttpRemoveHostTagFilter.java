/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.apache.http.protocol.HttpContext;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes the original OkHttp host tag, leaving only the new tags (target.schema, target.host, target.port) - aligned with tags naming of Apache HttpClient
 *
 * @author Jakub Marchwicki
 * @see io.micrometer.core.instrument.binder.httpcomponents.MicrometerHttpRequestExecutor#generateTagsForRoute(HttpContext)
 */
public class OkHttpRemoveHostTagFilter implements MeterFilter {

    @Override
    public Meter.Id map(Meter.Id id) {
        List<Tag> newTags = id.getTags().stream()
                .filter(t -> !t.getKey().equals("host")).collect(Collectors.toList());
        return id.replaceTags(newTags);
    }
}
