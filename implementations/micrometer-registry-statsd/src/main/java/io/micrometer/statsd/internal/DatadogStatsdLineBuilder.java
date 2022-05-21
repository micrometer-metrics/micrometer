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

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.util.DoubleFormat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

public class DatadogStatsdLineBuilder extends FlavorStatsdLineBuilder {

    private static final String TYPE_DISTRIBUTION = "d";

    private static final String ENTITY_ID_TAG_NAME = "dd.internal.entity_id";

    private final Object conventionTagsLock = new Object();

    @SuppressWarnings({ "NullableProblems", "unused" })
    private volatile NamingConvention namingConvention;

    @SuppressWarnings("NullableProblems")
    private volatile String name;

    @Nullable
    private volatile String conventionTags;

    @SuppressWarnings("NullableProblems")
    private volatile String tagsNoStat;

    private final ConcurrentMap<Statistic, String> tags = new ConcurrentHashMap<>();

    private final boolean percentileHistogram;

    // VisibleForTesting
    @Nullable
    String ddEntityId;

    public DatadogStatsdLineBuilder(Meter.Id id, MeterRegistry.Config config) {
        this(id, config, null);
    }

    /**
     * Create a {@code DatadogStatsdLineBuilder} instance.
     * @param id meter ID
     * @param config meter registry configuration
     * @param distributionStatisticConfig distribution statistic configuration
     * @since 1.8.0
     */
    public DatadogStatsdLineBuilder(Meter.Id id, MeterRegistry.Config config,
            @Nullable DistributionStatisticConfig distributionStatisticConfig) {
        super(id, config);

        percentileHistogram = distributionStatisticConfig != null
                && TRUE.equals(distributionStatisticConfig.isPercentileHistogram());
        ddEntityId = System.getenv("DD_ENTITY_ID");
    }

    @Override
    public String timing(double timeMs) {
        if (percentileHistogram) {
            return distributionLine(timeMs);
        }
        else {
            return super.timing(timeMs);
        }
    }

    @Override
    public String histogram(double amount) {
        if (percentileHistogram) {
            return distributionLine(amount);
        }
        else {
            return super.histogram(amount);
        }
    }

    private String distributionLine(double amount) {
        return line(DoubleFormat.decimalOrNan(amount), null, TYPE_DISTRIBUTION);
    }

    @Override
    String line(String amount, @Nullable Statistic stat, String type) {
        updateIfNamingConventionChanged();
        return name + amount + "|" + type + tagsByStatistic(stat);
    }

    private void updateIfNamingConventionChanged() {
        NamingConvention next = config.namingConvention();
        if (this.namingConvention != next) {
            synchronized (conventionTagsLock) {
                if (this.namingConvention == next) {
                    return;
                }
                this.tags.clear();
                String conventionTags = id.getTagsAsIterable().iterator().hasNext()
                        ? id.getConventionTags(next).stream().map(this::formatTag).collect(Collectors.joining(","))
                        : null;
                this.conventionTags = appendEntityIdTag(conventionTags);
            }
            this.name = next.name(sanitizeName(id.getName()), id.getType(), id.getBaseUnit()) + ":";
            this.tagsNoStat = tags(null, conventionTags, ":", "|#");
            this.namingConvention = next;
        }
    }

    @Nullable
    private String appendEntityIdTag(@Nullable String tags) {
        if (ddEntityId != null && !ddEntityId.trim().isEmpty()) {
            String entityIdTag = formatTag(Tag.of(ENTITY_ID_TAG_NAME, ddEntityId));
            return tags == null ? entityIdTag : tags + "," + entityIdTag;
        }
        return tags;
    }

    private String formatTag(Tag t) {
        String sanitizedTag = sanitizeName(t.getKey());
        if (!t.getValue().isEmpty()) {
            sanitizedTag += ":" + sanitizeTagValue(t.getValue());
        }
        return sanitizedTag;
    }

    private String sanitizeName(String value) {
        if (!Character.isLetter(value.charAt(0))) {
            value = "m." + value;
        }
        return value.replace(':', '_');
    }

    private String sanitizeTagValue(String value) {
        return (value.charAt(value.length() - 1) == ':') ? value.substring(0, value.length() - 1) + '_' : value;
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
            return this.tags.computeIfAbsent(stat, (key) -> tags(key, conventionTags, ":", "|#"));
        }
    }

}
