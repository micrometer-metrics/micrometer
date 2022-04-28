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
package io.micrometer.elastic;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for Elasticsearch.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public class ElasticNamingConvention implements NamingConvention {

    private static final Pattern FIRST_UNDERSCORE_PATTERN = Pattern.compile("^_+");

    private final NamingConvention delegate;

    public ElasticNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public ElasticNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return delegate.name(name, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        if (key.equals("name")) {
            key = "name.tag";
        }
        else if (key.equals("type")) {
            key = "type.tag";
        }
        else if (key.startsWith("_")) {
            // Fields that start with _ are considered reserved and ignored by Kibana. See
            // https://github.com/elastic/kibana/issues/2551
            key = FIRST_UNDERSCORE_PATTERN.matcher(key).replaceFirst("");
        }

        return delegate.tagKey(key);
    }

}
