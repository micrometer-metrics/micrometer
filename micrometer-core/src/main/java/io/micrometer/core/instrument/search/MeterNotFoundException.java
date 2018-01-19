/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.search;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

import java.util.List;
import java.util.stream.Collectors;

public class MeterNotFoundException extends RuntimeException {
    public MeterNotFoundException(String meterName, List<Tag> tags, Class<? extends Meter> meterType) {
        super("Unable to locate a meter named '" + meterName + "'" + tagDetail(tags) + " of type " + meterType.getCanonicalName());
    }

    private static String tagDetail(List<Tag> tags) {
        String tagDetail = "";
        if (!tags.isEmpty()) {
            tagDetail = " with Tags:[" + tags.stream().map(t -> t.getKey() + ":" + t.getValue()).collect(Collectors.joining(",")) + "]";
        }
        return tagDetail;
    }
}
