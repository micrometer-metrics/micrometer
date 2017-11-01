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
package io.micrometer.atlas;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import com.netflix.spectator.api.histogram.PercentileTimer;
import com.netflix.spectator.atlas.AtlasConfig;
import com.netflix.spectator.atlas.AtlasRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.StatsConfig;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class AtlasMeterRegistry extends MeterRegistry {
    private final AtlasRegistry registry;
    private final DecimalFormat percentileFormat = new DecimalFormat("#.####");

    public AtlasMeterRegistry(AtlasConfig config, Clock clock) {
        super(clock);

        this.registry = new AtlasRegistry(new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        }, config);

        // invalid character replacement happens in the spectator-reg-atlas module, so doesn't need
        // to be duplicated here.
        this.config().namingConvention(NamingConvention.camelCase);

        start();
    }

    public AtlasMeterRegistry(AtlasConfig config) {
        this(config, Clock.SYSTEM);
    }

    public void start() {
        getAtlasRegistry().start();
    }

    public void stop() {
        getAtlasRegistry().stop();
    }

    private AtlasRegistry getAtlasRegistry() {
        return (AtlasRegistry) this.getSpectatorRegistry();
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(Meter.Id id) {
        return new SpectatorCounter(id, registry.counter(spectatorId(id)));
    }

    @Override
    protected io.micrometer.core.instrument.DistributionSummary newDistributionSummary(Meter.Id id, StatsConfig statsConfig) {
        com.netflix.spectator.api.DistributionSummary internalSummary = registry.distributionSummary(spectatorId(id));

        if(statsConfig.isPercentileHistogram()) {
            // This doesn't report the normal count/totalTime/max stats, so we treat it as additive
            PercentileDistributionSummary.get(registry, spectatorId(id));
        }

        SpectatorDistributionSummary summary = new SpectatorDistributionSummary(id, internalSummary, clock, statsConfig);

        for (long sla : statsConfig.getSlaBoundaries()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "sla", Long.toString(sla)), sla, summary::histogramCountAtValue);
        }

        for (double percentile : statsConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, summary::percentile);
        }

        return summary;
    }

    @Override
    protected Timer newTimer(Meter.Id id, StatsConfig statsConfig) {
        com.netflix.spectator.api.Timer internalTimer = registry.timer(spectatorId(id));

        if(statsConfig.isPercentileHistogram()) {
            // This doesn't report the normal count/totalTime/max stats, so we treat it as additive
            PercentileTimer.get(registry, spectatorId(id));
        }

        SpectatorTimer timer = new SpectatorTimer(id, internalTimer, clock, statsConfig);

        for (long sla : statsConfig.getSlaBoundaries()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "sla", Duration.ofNanos(sla).toString()), sla, timer::histogramCountAtValue);
        }

        for (double percentile : statsConfig.getPercentiles()) {
            gauge(id.getName(), Tags.concat(getConventionTags(id), "percentile", percentileFormat.format(percentile)),
                percentile, p -> timer.percentile(p, TimeUnit.SECONDS));
        }

        return timer;
    }

    private Id spectatorId(Meter.Id id) {
        List<com.netflix.spectator.api.Tag> tags = getConventionTags(id).stream()
            .map(t -> new BasicTag(t.getKey(), t.getValue()))
            .collect(toList());
        return registry.createId(getConventionName(id), tags);
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        com.netflix.spectator.api.Gauge gauge = new SpectatorToDoubleGauge<>(registry.clock(), spectatorId(id), obj, f);
        registry.register(gauge);
        return new SpectatorGauge(id, gauge);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new SpectatorLongTaskTimer(id, com.netflix.spectator.api.patterns.LongTaskTimer.get(registry, spectatorId(id)));
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<io.micrometer.core.instrument.Measurement> measurements) {
        Id spectatorId = spectatorId(id);
        com.netflix.spectator.api.AbstractMeter<Id> spectatorMeter = new com.netflix.spectator.api.AbstractMeter<Id>(registry.clock(), spectatorId, spectatorId) {
            @Override
            public Iterable<com.netflix.spectator.api.Measurement> measure() {
                return stream(measurements.spliterator(), false)
                    .map(m -> new com.netflix.spectator.api.Measurement(id, clock.wallTime(), m.getValue()))
                    .collect(toList());
            }
        };
        registry.register(spectatorMeter);
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }
}
