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
package io.micrometer.dynatrace.v1;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.StringEscapeUtils;

import java.util.Map;
import java.util.stream.Collectors;

class DynatraceTimeSeries {

    private final String metricId;

    private final Map<String, String> dimensions;

    private final long time;

    private final double value;

    DynatraceTimeSeries(final String metricId, final long time, final double value,
            @Nullable final Map<String, String> dimensions) {
        this.metricId = metricId;
        this.dimensions = dimensions;
        this.time = time;
        this.value = value;
    }

    public String getMetricId() {
        return metricId;
    }

    String asJson() {
        String body = "{\"timeseriesId\":\"" + metricId + "\"" + ",\"dataPoints\":[[" + time + ","
                + DoubleFormat.wholeOrDecimal(value) + "]]";

        if (dimensions != null && !dimensions.isEmpty()) {
            body += ",\"dimensions\":{" + dimensions.entrySet()
                .stream()
                .map(t -> "\"" + t.getKey() + "\":\"" + StringEscapeUtils.escapeJson(t.getValue()) + "\"")
                .collect(Collectors.joining(",")) + "}";
        }
        body += "}";
        return body;
    }

}
