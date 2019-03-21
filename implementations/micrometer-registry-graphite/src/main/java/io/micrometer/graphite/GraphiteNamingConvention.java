/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.graphite;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * {@link NamingConvention} for Graphite.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class GraphiteNamingConvention implements NamingConvention {
    /**
     * A list that probably is blacklisted: https://github.com/graphite-project/graphite-web/blob/master/webapp/graphite/render/grammar.py#L48-L55.
     * Empirically, we have found others.
     */
    private static final Pattern blacklistedChars = Pattern.compile("[{}(),=\\[\\]/]");
    private final NamingConvention delegate;

    public GraphiteNamingConvention() {
        this(NamingConvention.camelCase);
    }

    public GraphiteNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return sanitize(this.delegate.name(normalize(name), type, baseUnit));
    }

    @Override
    public String tagKey(String key) {
        return sanitize(this.delegate.tagKey(normalize(key)));
    }

    @Override
    public String tagValue(String value) {
        return sanitize(this.delegate.tagValue(normalize(value)));
    }

    /**
     * Github Issue: https://github.com/graphite-project/graphite-web/issues/243
     * Unicode is not OK. Some special chars are not OK.
     */
    private String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKD);
    }

    private String sanitize(String delegated) {
        return blacklistedChars.matcher(delegated).replaceAll("_");
    }

}
