/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.influx;

import io.micrometer.core.instrument.TagFormatter;

public class InfluxTagFormatter implements TagFormatter {
    @Override
    public String formatName(String name) {
        return format(name.replace("=", "_"));
    }

    @Override
    public String formatTagKey(String key) {
        // `time` cannot be a field key or tag key
        if(key.equals("time"))
            throw new IllegalArgumentException("'time' is an invalid tag key in InfluxDB");
        return format(key);
    }

    @Override
    public String formatTagValue(String value) {
        // `time` cannot be a field key or tag key
        if(value.equals("time"))
            throw new IllegalArgumentException("'time' is an invalid tag value in InfluxDB");
        return format(value);
    }

    private String format(String name) {
        // https://docs.influxdata.com/influxdb/v1.3/write_protocols/line_protocol_reference/#special-characters
        return name.replace(",", "\\,")
                .replace(" ", "\\ ")
                .replace("=", "\\=")
                .replace("\"", "\\\"");
    }
}
