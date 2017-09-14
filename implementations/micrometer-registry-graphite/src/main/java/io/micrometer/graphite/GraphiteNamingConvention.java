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
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.NamingConvention;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class GraphiteNamingConvention implements NamingConvention {
    /**
     * A list that probably is blacklisted: https://github.com/graphite-project/graphite-web/blob/master/webapp/graphite/render/grammar.py#L48-L55.
     * Empirically, we have found others.
     */
    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return format(name);
    }

    @Override
    public String tagKey(String key) {
        return format(key);
    }

    @Override
    public String tagValue(String value) {
        return format(value);
    }

    /**
     * Github Issue: https://github.com/graphite-project/graphite-web/issues/243

     * Unicode is not OK. Some special chars are not OK.
     */
    private String format(String name) {
        String sanitized = Normalizer.normalize(name, Normalizer.Form.NFKD);
        sanitized = NamingConvention.camelCase.tagKey(sanitized);
        return blacklistedChars.matcher(sanitized).replaceAll("_");
    }
}
