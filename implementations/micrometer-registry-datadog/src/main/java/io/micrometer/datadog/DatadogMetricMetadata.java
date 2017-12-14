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

import java.util.*;

/**
 * @author Jon Schneider
 */
class DatadogMetricMetadata {
    private final Meter.Id id;
    private final String type;
    private final String overrideBaseUnit;
    private final boolean descriptionsEnabled;

    // Datadog rejects anything not on this list: https://docs.datadoghq.com/units/
    private final Set<String> unitWhitelist = new HashSet<>(Arrays.asList(
        "bit", "byte", "kilobyte", "megabyte", "gigabyte", "terabyte", "petabyte", "exobyte",
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
        "entry"));

    private final Map<String, String> pluralizedUnitMapping = new HashMap<>();

    DatadogMetricMetadata(Meter.Id id, Statistic statistic, boolean descriptionsEnabled, String overrideBaseUnit) {
        this.id = id;
        this.descriptionsEnabled = descriptionsEnabled;
        this.overrideBaseUnit = overrideBaseUnit;

        this.unitWhitelist.forEach(unit -> pluralizedUnitMapping.put(unit + "s", unit));
        this.pluralizedUnitMapping.put("indices", "index");
        this.pluralizedUnitMapping.put("indexes", "index");

        switch (statistic) {
            case Count:
            case Total:
            case TotalTime:
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
            String whitelistedBaseUnit = unitWhitelist.contains(baseUnit) ? baseUnit :
                pluralizedUnitMapping.get(baseUnit);

            if(whitelistedBaseUnit != null) {
                body += ",\"unit\":\"" + whitelistedBaseUnit + "\"";
            }
        }

        if (descriptionsEnabled && id.getDescription() != null) {
            body += ",\"description\":\"" + id.getDescription() + "\"";
        }

        body += "}";

        return body;
    }
}
