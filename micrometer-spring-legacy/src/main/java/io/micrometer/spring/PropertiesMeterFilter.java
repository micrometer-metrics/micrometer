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
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.Nullable;
import io.micrometer.spring.autoconfigure.MetricsProperties;

import java.time.Duration;
import java.util.function.Function;

@NonNullApi
public class PropertiesMeterFilter implements MeterFilter {
    private final MetricsProperties props;

    public PropertiesMeterFilter(MetricsProperties props) {
        this.props = props;
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        Boolean enabled = getMostSpecific(name -> props.getEnabled().get(name), id.getName());
        if (enabled != null && !enabled)
            return MeterFilterReply.DENY;
        return MeterFilterReply.NEUTRAL;
    }

    @Nullable
    private <V> V getMostSpecific(Function<String, V> lookup, String k) {
        V v = lookup.apply(k.isEmpty() ? "" : k);
        if (v != null)
            return v;
        else if (k.isEmpty() || k.equals("all")) {
            return null;
        }

        int lastSep = k.lastIndexOf('.');
        if (lastSep == -1)
            return getMostSpecific(lookup, "all");

        return getMostSpecific(lookup, k.substring(0, lastSep));
    }

    @Override
    public HistogramConfig configure(Meter.Id id, HistogramConfig config) {
        if (!id.getType().equals(Meter.Type.TIMER) && !id.getType().equals(Meter.Type.DISTRIBUTION_SUMMARY))
            return config;

        HistogramConfig.Builder builder = HistogramConfig.builder();

        Boolean percentileHistogram = getMostSpecific(
            name -> props.getSummaries().getPercentileHistogram().getOrDefault(name, props.getTimers().getPercentileHistogram().get(name)),
            id.getName());
        if (percentileHistogram != null)
            builder.percentilesHistogram(percentileHistogram);

        double[] percentiles = getMostSpecific(
            name -> props.getSummaries().getPercentiles().getOrDefault(name, props.getTimers().getPercentiles().get(name)),
            id.getName());

        if (percentiles != null) {
            builder.percentiles(percentiles);
        }

        Integer histogramBufferLength = getMostSpecific(
            name -> props.getSummaries().getHistogramBufferLength().getOrDefault(name, props.getTimers().getHistogramBufferLength().get(name)),
            id.getName());
        if (histogramBufferLength != null) {
            builder.histogramBufferLength(histogramBufferLength);
        }

        Duration histogramExpiry = getMostSpecific(
            name -> props.getSummaries().getHistogramExpiry().getOrDefault(name, props.getTimers().getHistogramExpiry().get(name)),
            id.getName());
        if (histogramExpiry != null) {
            builder.histogramExpiry(histogramExpiry);
        }

        if (id.getType().equals(Meter.Type.TIMER)) {
            Duration max = getMostSpecific(name -> props.getTimers().getMaximumExpectedValue().get(name), id.getName());
            if (max != null) {
                builder.maximumExpectedValue(max.toNanos());
            }

            Duration min = getMostSpecific(name -> props.getTimers().getMinimumExpectedValue().get(name), id.getName());
            if (min != null) {
                builder.minimumExpectedValue(min.toNanos());
            }

            Duration[] sla = getMostSpecific(name -> props.getTimers().getSla().get(name), id.getName());
            if (sla != null) {
                long[] slaNanos = new long[sla.length];
                for (int i = 0; i < sla.length; i++) {
                    slaNanos[i] = sla[i].toNanos();
                }
                builder.sla(slaNanos);
            }
        } else if (id.getType().equals(Meter.Type.DISTRIBUTION_SUMMARY)) {
            Long max = getMostSpecific(name -> props.getSummaries().getMaximumExpectedValue().get(name), id.getName());
            if (max != null) {
                builder.maximumExpectedValue(max);
            }

            Long min = getMostSpecific(name -> props.getSummaries().getMinimumExpectedValue().get(name), id.getName());
            if (min != null) {
                builder.minimumExpectedValue(min);
            }

            long[] sla = getMostSpecific(name -> props.getSummaries().getSla().get(name), id.getName());
            if (sla != null)
                builder.sla(sla);
        }

        return builder.build().merge(config);
    }
}
