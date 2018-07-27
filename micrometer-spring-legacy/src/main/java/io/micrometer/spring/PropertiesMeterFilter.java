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
package io.micrometer.spring;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;
import io.micrometer.spring.autoconfigure.MetricsProperties;
import io.micrometer.spring.autoconfigure.ServiceLevelAgreementBoundary;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link MeterFilter} to apply settings from {@link MetricsProperties}.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @author Stephane Nicoll
 */
@NonNullApi
public class PropertiesMeterFilter implements MeterFilter {

    private final MetricsProperties properties;

    private final MeterFilter mapFilter;

    public PropertiesMeterFilter(MetricsProperties properties) {
        Assert.notNull(properties, "Properties must not be null");
        this.properties = properties;
        this.mapFilter = createMapFilter(properties.getTags());
    }

    private static MeterFilter createMapFilter(Map<String, String> tags) {
        if (tags.isEmpty()) {
            return new MeterFilter() {
            };
        }
        List<Tag> commonTags = tags.entrySet().stream()
                .map((entry) -> Tag.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return MeterFilter.commonTags(commonTags);
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        boolean enabled = lookup(this.properties.getEnable(), id, true);
        return (enabled ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY);
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        return this.mapFilter.map(id);
    }

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        MetricsProperties.Distribution distribution = this.properties.getDistribution();
        return DistributionStatisticConfig.builder()
                .percentilesHistogram(lookup(distribution.getPercentilesHistogram(), id, null))
                .percentiles(lookup(distribution.getPercentiles(), id, null))
                .sla(convertSla(id.getType(), lookup(distribution.getSla(), id, null)))
                .build()
                .merge(config);
    }

    @Nullable
    private long[] convertSla(Meter.Type meterType, @Nullable ServiceLevelAgreementBoundary[] sla) {
        if (sla == null) {
            return null;
        }
        long[] converted = Arrays.stream(sla)
                .map((candidate) -> candidate.getValue(meterType))
                .filter(Objects::nonNull).mapToLong(Long::longValue).toArray();
        return converted.length == 0 ? null : converted;
    }

    private <T> T lookup(Map<String, T> values, Meter.Id id, @Nullable T defaultValue) {
        if (values.isEmpty()) {
            return defaultValue;
        }

        String name = id.getName();
        while (StringUtils.hasLength(name)) {
            T result = values.get(name);
            if (result != null) {
                return result;
            }
            int lastDot = name.lastIndexOf('.');
            name = lastDot == -1 ? "" : name.substring(0, lastDot);
        }
        return values.getOrDefault("all", defaultValue);
    }
}
