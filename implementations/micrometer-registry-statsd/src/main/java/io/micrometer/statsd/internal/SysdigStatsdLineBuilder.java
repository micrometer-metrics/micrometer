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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SysdigStatsdLineBuilder extends FlavorStatsdLineBuilder {

    private final Object conventionTagsLock = new Object();

    private volatile NamingConvention namingConvention;

    private volatile String name;

    private volatile @Nullable String conventionTags;

    private volatile String tagsNoStat;

    private final ConcurrentMap<Statistic, String> tags = new ConcurrentHashMap<>();

    private static final Pattern NAME_WHITELIST = Pattern.compile("[^\\w._]");

    public SysdigStatsdLineBuilder(Meter.Id id, MeterRegistry.Config config) {
        super(id, config);

        this.namingConvention = config.namingConvention();
        this.name = createName(namingConvention);
        this.conventionTags = createConventionTags(namingConvention);
        this.tagsNoStat = createTagsNoStat(conventionTags);
    }

    @Override
    String line(String amount, @Nullable Statistic stat, String type) {
        updateIfNamingConventionChanged();
        return name + tagsByStatistic(stat) + ":" + amount + "|" + type;
    }

    private void updateIfNamingConventionChanged() {
        NamingConvention next = config.namingConvention();
        if (this.namingConvention != next) {
            this.name = createName(next);
            synchronized (conventionTagsLock) {
                this.tags.clear();
                this.conventionTags = createConventionTags(next);
            }
            this.tagsNoStat = createTagsNoStat(conventionTags);
            this.namingConvention = next;
        }
    }

    private @Nullable String createConventionTags(NamingConvention namingConvention) {
        return id.getTagsAsIterable().iterator().hasNext() ? id.getConventionTags(namingConvention)
            .stream()
            .map(t -> sanitize(t.getKey()) + "=" + sanitize(t.getValue()))
            .collect(Collectors.joining(",")) : null;
    }

    private String createName(NamingConvention namingConvention) {
        return sanitize(namingConvention.name(id.getName(), id.getType(), id.getBaseUnit()));
    }

    private String createTagsNoStat(@Nullable String conventionTags) {
        return tags(null, conventionTags, "=", "#");
    }

    private static String sanitize(String name) {
        return NAME_WHITELIST.matcher(name).replaceAll("_");
    }

    private String tagsByStatistic(@Nullable Statistic stat) {
        if (stat == null) {
            return tagsNoStat;
        }
        String tags = this.tags.get(stat);
        if (tags != null) {
            return tags;
        }
        synchronized (conventionTagsLock) {
            return this.tags.computeIfAbsent(stat, (key) -> tags(stat, conventionTags, "=", "#"));
        }
    }

}
