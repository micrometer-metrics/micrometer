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
package io.micrometer.influx;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for Influx.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class InfluxNamingConvention implements NamingConvention {

    // https://docs.influxdata.com/influxdb/v1.3/write_protocols/line_protocol_reference/#special-characters
    private static final Pattern PATTERN_SPECIAL_CHARACTERS = Pattern.compile("([, =\"])");

    private final NamingConvention delegate;

    /**
     * By default, telegraf's configuration option for {@code metric_separator} is an
     * underscore, which corresponds to {@link NamingConvention#snakeCase}.
     */
    public InfluxNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public InfluxNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return escape(delegate.name(name, type, baseUnit).replace("=", "_"));
    }

    @Override
    public String tagKey(String key) {
        // `time` cannot be a field key or tag key
        if (key.equals("time")) {
            throw new IllegalArgumentException("'time' is an invalid tag key in InfluxDB");
        }
        return escape(delegate.tagKey(key));
    }

    @Override
    public String tagValue(String value) {
        return escape(this.delegate.tagValue(value).replace('\n', ' '));
    }

    private String escape(String string) {
        return PATTERN_SPECIAL_CHARACTERS.matcher(string).replaceAll("\\\\$1");
    }

}
