/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.health;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Configured with a set of queries, provides an overall health indicator given a set of
 * service level objectives.
 * <p>
 * For efficiency, this registry automatically denies all metrics that aren't part of the
 * definition of a service level objective.
 * <p>
 * Service level objectives can specify one or more {@link MeterBinder} that they require
 * to be registered in order to perform their tests. These are automatically bound at
 * construction time.
 *
 * @author Jon Schneider
 * @since 1.6.0
 */
@Incubating(since = "1.6.0")
public class HealthMeterRegistry extends SimpleMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("health-metrics-ticker");

    private final HealthConfig config;

    private final Collection<ServiceLevelObjective> serviceLevelObjectives;

    private final Collection<MeterFilter> serviceLevelObjectiveFilters;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected HealthMeterRegistry(HealthConfig config, Collection<ServiceLevelObjective> serviceLevelObjectives,
            Collection<MeterFilter> serviceLevelObjectiveFilters, Clock clock, ThreadFactory threadFactory) {
        super(new SimpleConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public Duration step() {
                return config.step();
            }

            @Override
            public final CountingMode mode() {
                return CountingMode.STEP;
            }
        }, clock);

        config.requireValid();

        this.config = config;
        this.serviceLevelObjectives = serviceLevelObjectives;
        this.serviceLevelObjectiveFilters = serviceLevelObjectiveFilters;

        for (ServiceLevelObjective slo : serviceLevelObjectives) {
            for (MeterFilter filter : slo.getAcceptFilters()) {
                config().meterFilter(filter);
            }
        }

        // deny all metrics that aren't specifically indicators used to measure SLOs
        config().meterFilter(MeterFilter.deny());

        // do this after the deny filter is set, because maybe only a portion of the
        // metrics a binder registers are needed
        // for the SLOs that require the binder
        for (ServiceLevelObjective slo : serviceLevelObjectives) {
            for (MeterBinder require : slo.getRequires()) {
                require.bindTo(this);
            }
        }

        start(threadFactory);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }

    public static Builder builder(HealthConfig config) {
        return new Builder(config);
    }

    public static class Builder {

        private final HealthConfig config;

        private final Collection<ServiceLevelObjective> serviceLevelObjectives = new ArrayList<>();

        private final Collection<MeterFilter> serviceLevelObjectiveFilters = new ArrayList<>();

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        Builder(HealthConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder serviceLevelObjectives(ServiceLevelObjective... slos) {
            Collections.addAll(this.serviceLevelObjectives, slos);
            return this;
        }

        public Builder serviceLevelObjectiveFilter(MeterFilter filter) {
            this.serviceLevelObjectiveFilters.add(filter);
            return this;
        }

        public HealthMeterRegistry build() {
            return new HealthMeterRegistry(config, serviceLevelObjectives, serviceLevelObjectiveFilters, clock,
                    threadFactory);
        }

    }

    void tick() {
        serviceLevelObjectives.forEach(slo -> slo.tick(this));
    }

    public Collection<ServiceLevelObjective> getServiceLevelObjectives() {
        return serviceLevelObjectives.stream()
            .filter(slo -> accept(slo.getId()))
            .map(slo -> serviceLevelObjectiveFilters.stream()
                .reduce(slo,
                        (filtered, filter) -> new ServiceLevelObjective.FilteredServiceLevelObjective(
                                filter.map(filtered.getId()), filtered),
                        (obj1, obj2) -> obj2))
            .collect(Collectors.toList());
    }

    private boolean accept(Meter.Id id) {
        for (MeterFilter filter : serviceLevelObjectiveFilters) {
            MeterFilterReply reply = filter.accept(id);
            if (reply == MeterFilterReply.DENY) {
                return false;
            }
            else if (reply == MeterFilterReply.ACCEPT) {
                return true;
            }
        }
        return true;
    }

    public void start(ThreadFactory threadFactory) {
        if (scheduledExecutorService != null)
            stop();

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
        scheduledExecutorService.scheduleAtFixedRate(this::tick, config.step().toMillis(), config.step().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

}
