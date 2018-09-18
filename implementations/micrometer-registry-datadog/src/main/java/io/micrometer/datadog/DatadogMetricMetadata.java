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
package io.micrometer.datadog;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.lang.Nullable;

import java.util.*;

/**
 * @author Jon Schneider
 */
class DatadogMetricMetadata {

    // Datadog rejects anything not on this list: https://docs.datadoghq.com/units/
    private static final Set<String> UNIT_WHITELIST = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "bit", "byte", "kibibyte", "mebibyte", "gibibyte", "tebibyte", "pebibyte", "exbibyte",
        "microsecond", "millisecond", "second", "minute", "hour", "day", "week", "nanosecond",
        "fraction", "percent", "percent_nano", "apdex",
        "connection", "request", "packet", "segment", "response", "message", "payload", "timeout", "datagram", "route", "session",
        "process", "core", "thread", "host", "node", "fault", "service", "instance", "cpu",
        "file", "inode", "sector", "block",
        "buffer", "error", "read", "write", "occurrence", "event", "time", "unit", "operation", "item", "task", "worker", "resource",
        "email", "sample", "stage", "monitor", "location", "check", "attempt", "device", "update", "method", "job", "container",
        "table", "index", "lock", "transaction", "query", "row", "key", "command", "offset", "record", "object", "cursor", "assertion", "scan", "document", "shard", "flush", "merge", "refresh", "fetch", "column", "commit", "wait", "ticket", "question",
        "hit", "miss", "eviction", "get", "set",
        "dollar", "cent",
        "page", "split",
        "hertz", "kilohertz", "megahertz", "gigahertz",
        "entry")));

    private static final Map<String, String> PLURALIZED_UNIT_MAPPING;

    static {
        Map<String, String> pluralizedUnitMapping = new HashMap<>();
        UNIT_WHITELIST.forEach(unit -> pluralizedUnitMapping.put(unit + "s", unit));
        pluralizedUnitMapping.put("indices", "index");
        pluralizedUnitMapping.put("indexes", "index");
        PLURALIZED_UNIT_MAPPING = Collections.unmodifiableMap(pluralizedUnitMapping);
    }

    private final Meter.Id id;
    private final String type;
    private final boolean descriptionsEnabled;

    @Nullable private final String overrideBaseUnit;

    DatadogMetricMetadata(Meter.Id id, Statistic statistic, boolean descriptionsEnabled,
                          @Nullable String overrideBaseUnit) {
        this.id = id;
        this.descriptionsEnabled = descriptionsEnabled;
        this.overrideBaseUnit = overrideBaseUnit;

        switch (statistic) {
            case COUNT:
            case TOTAL:
            case TOTAL_TIME:
                this.type = "count";
                break;
            default:
                this.type = "gauge";
        }
    }

    String editMetadataBody() {
        String body = "{\"type\":\"" + type + "\"";

        String baseUnit = overrideBaseUnit != null ? overrideBaseUnit : id.getBaseUnit();
        if (baseUnit != null) {
            String whitelistedBaseUnit = UNIT_WHITELIST.contains(baseUnit) ? baseUnit :
                PLURALIZED_UNIT_MAPPING.get(baseUnit);

            if (whitelistedBaseUnit != null) {
                body += ",\"unit\":\"" + whitelistedBaseUnit + "\"";
            }
        }

        if (descriptionsEnabled && id.getDescription() != null) {
            body += ",\"description\":\"" + StringEscapeUtils.escapeJson(id.getDescription()) + "\"";
        }

        body += "}";

        return body;
    }
}
