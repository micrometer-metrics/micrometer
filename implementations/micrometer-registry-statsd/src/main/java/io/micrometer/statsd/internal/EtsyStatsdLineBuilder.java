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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

public class EtsyStatsdLineBuilder extends FlavorStatsdLineBuilder {
    private final HierarchicalNameMapper nameMapper;

    @SuppressWarnings({"NullableProblems", "unused"})
    private volatile NamingConvention namingConvention;

    @Nullable
    private volatile String nameNoStat;

    private final Object namesLock = new Object();
    private volatile PMap<Statistic, String> names = HashTreePMap.empty();

    public EtsyStatsdLineBuilder(Meter.Id id, MeterRegistry.Config config, HierarchicalNameMapper nameMapper) {
        super(id, config);
        this.nameMapper = nameMapper;
    }

    @Override
    String line(String amount, @Nullable Statistic stat, String type) {
        updateIfNamingConventionChanged();
        return nameByStatistic(stat) + ":" + amount + "|" + type;
    }

    private void updateIfNamingConventionChanged() {
        NamingConvention next = config.namingConvention();
        if (this.namingConvention != next) {
            this.namingConvention = next;
            this.names = HashTreePMap.empty();
            this.nameNoStat = null;
        }
    }

    private String nameByStatistic(@Nullable Statistic stat) {
        if (stat == null) {
            if (this.nameNoStat == null) {
                this.nameNoStat = etsyName(null);
            }
            //noinspection ConstantConditions
            return nameNoStat;
        }

        String nameString = names.get(stat);
        if (nameString != null)
            return nameString;

        synchronized (namesLock) {
            nameString = names.get(stat);
            if (nameString != null) {
                return nameString;
            }

            nameString = etsyName(stat);
            names = names.plus(stat, nameString);
            return nameString;
        }
    }

    private String etsyName(@Nullable Statistic stat) {
        return nameMapper.toHierarchicalName(stat != null ? id.withTag(stat) : id, config.namingConvention())
                .replace(':', '_');
    }
}
