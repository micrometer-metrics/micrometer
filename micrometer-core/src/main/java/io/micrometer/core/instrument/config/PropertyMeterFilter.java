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
package io.micrometer.core.instrument.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.histogram.HistogramConfig;

import java.time.Duration;

/**
 * A meter filter built from a set of properties.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 */
public abstract class PropertyMeterFilter implements MeterFilter {
    public abstract <V> V get(String k, Class<V> vClass);

    private <V> V getMostSpecific(String k, String suffix, Class<V> vClass) {
        V v = get(k.isEmpty() ? suffix : k + "." + suffix, vClass);
        if(v != null)
            return v;
        else if(k.isEmpty()) {
            return null;
        }

        int lastSep = k.lastIndexOf('.');
        if(lastSep == -1)
            return getMostSpecific("", suffix, vClass);

        return getMostSpecific(k.substring(0, lastSep), suffix, vClass);
    }

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        Boolean enabled = getMostSpecific(id.getName(), "enabled", Boolean.class);
        if(enabled == null)
            return MeterFilterReply.NEUTRAL;
        return enabled ? MeterFilterReply.ACCEPT : MeterFilterReply.DENY;
    }

    @Override
    public HistogramConfig configure(Meter.Id id, HistogramConfig histogramConfig) {
        if(!id.getType().equals(Meter.Type.Timer) && !id.getType().equals(Meter.Type.DistributionSummary))
            return histogramConfig;

        HistogramConfig.Builder builder = HistogramConfig.builder();

        Boolean percentileHistogram = getMostSpecific(id.getName(), "percentileHistogram", Boolean.class);
        if(percentileHistogram != null)
            builder.percentilesHistogram(percentileHistogram);

        double[] percentiles = getMostSpecific(id.getName(), "percentiles", double[].class);
        if(percentiles != null)
            builder.percentiles(percentiles);

        Integer histogramBufferLength = getMostSpecific(id.getName(), "histogramBufferLength", Integer.class);
        if(histogramBufferLength != null) {
            builder.histogramBufferLength(histogramBufferLength);
        }

        Duration histogramExpiry = getMostSpecific(id.getName(), "histogramExpiry", Duration.class);
        if(histogramExpiry != null) {
            builder.histogramExpiry(histogramExpiry);
        }

        if(id.getType().equals(Meter.Type.Timer)) {
            Duration max = getMostSpecific(id.getName(), "maximumExpectedValue", Duration.class);
            if (max != null) {
                builder.maximumExpectedValue(max.toNanos());
            }

            Duration min = getMostSpecific(id.getName(), "minimumExpectedValue", Duration.class);
            if(min != null) {
                builder.minimumExpectedValue(min.toNanos());
            }

            Duration[] sla = getMostSpecific(id.getName(), "sla", Duration[].class);
            if(sla != null) {
                long[] slaNanos = new long[sla.length];
                for (int i = 0; i < sla.length; i++) {
                    slaNanos[i] = sla[i].toNanos();
                }
                builder.sla(slaNanos);
            }
        }
        else if(id.getType().equals(Meter.Type.DistributionSummary)) {
            Long max = getMostSpecific(id.getName(), "maximumExpectedValue", Long.class);
            if (max != null) {
                builder.maximumExpectedValue(max);
            }

            Long min = getMostSpecific(id.getName(), "minimumExpectedValue", Long.class);
            if(min != null) {
                builder.minimumExpectedValue(min);
            }

            long[] sla = getMostSpecific(id.getName(), "sla", long[].class);
            if(sla != null)
                builder.sla(sla);
        }

        return builder.build().merge(histogramConfig);
    }
}