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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WavefrontNamingConvention implements NamingConvention {
    private final Logger logger = LoggerFactory.getLogger(WavefrontNamingConvention.class);
    private final NamingConvention delegate;
    String namePrefix;

    public WavefrontNamingConvention() {
        // TODO which is the most common format?
        this(NamingConvention.dot);
    }

    public WavefrontNamingConvention(WavefrontConfig config)
    {
        this();
        // search for the prefix and set it if found any
        if(config.getNamePrefix() != null && config.getNamePrefix().trim().length() > 0)
        {
            namePrefix = config.getNamePrefix();
            logger.debug("[convention]namePrefix is " + namePrefix);
        }
    }

    public WavefrontNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    /**
     * Valid characters are: a-z, A-Z, 0-9, hyphen ("-"), underscore ("_"), dot ("."). Forward slash ("/") and comma
     * (",") are allowed if metricName is enclosed in double quotes.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        // TODO sanitize names of unacceptable characters

        // add name prefix if prefix exists
        if(namePrefix != null) return namePrefix + "." + delegate.name(name, type, baseUnit);
        return delegate.name(name, type, baseUnit);
    }

    /**
     * Valid characters are: alphanumeric, hyphen ("-"), underscore ("_"), dot (".")
     */
    @Override
    public String tagKey(String key) {
        // TODO sanitize tag keys of unacceptable characters
        return delegate.tagKey(key);
    }

    /**
     * We recommend enclosing tag values with double quotes (" "). If you surround the value with quotes any character is allowed,
     * including spaces. To include a double quote, escape it with a backslash. The backslash cannot
     * be the last character in the tag value.
     */
    @Override
    public String tagValue(String value) {
        // TODO sanitize tag values of unacceptable characters
        return delegate.tagValue(value);
    }
}
