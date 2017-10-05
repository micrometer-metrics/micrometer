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
package io.micrometer.influx;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;

/**
 * @author Jon Schneider
 */
public class InfluxNamingConvention implements NamingConvention {
    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return format(name.replace("=", "_"));
    }

    @Override
    public String tagKey(String key) {
        // `time` cannot be a field key or tag key
        if (key.equals("time"))
            throw new IllegalArgumentException("'time' is an invalid tag key in InfluxDB");
        return format(key);
    }

    @Override
    public String tagValue(String value) {
        // `time` cannot be a field key or tag key
        if (value.equals("time"))
            throw new IllegalArgumentException("'time' is an invalid tag value in InfluxDB");
        return format(value);
    }

    private String format(String name) {
        // https://docs.influxdata.com/influxdb/v1.3/write_protocols/line_protocol_reference/#special-characters
        return NamingConvention.camelCase.tagKey(name)
            .replace(",", "\\,")
            .replace(" ", "\\ ")
            .replace("=", "\\=")
            .replace("\"", "\\\"");
    }
}
