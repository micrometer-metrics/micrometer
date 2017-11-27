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
package io.micrometer.jersey2.server;

import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Builder;

/**
 * Micrometer {@link RequestEventListener} recording metrics for Jersey server
 * requests.
 * 
 * @author Michael Weirauch
 */
public class MicrometerRequestEventListener implements RequestEventListener {

    private static final Logger log = Logger
            .getLogger(MicrometerRequestEventListener.class.getSimpleName());

    private static final Map<ContainerRequest, Long> longTaskTimerIds = Collections
            .synchronizedMap(new IdentityHashMap<>());

    private final MeterRegistry meterRegistry;

    private final JerseyTagsProvider tagsProvider;

    private final String metricName;

    private final boolean autoTimeRequests;

    private final boolean recordRequestPercentiles;

    private Long startTime;

    public MicrometerRequestEventListener(MeterRegistry meterRegistry,
            JerseyTagsProvider tagsProvider, String metricName, boolean autoTimeRequests,
            boolean recordRequestPercentiles) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.tagsProvider = requireNonNull(tagsProvider);
        this.metricName = requireNonNull(metricName);
        this.autoTimeRequests = autoTimeRequests;
        this.recordRequestPercentiles = recordRequestPercentiles;
    }

    @Override
    public void onEvent(RequestEvent event) {
        switch (event.getType()) {
        case ON_EXCEPTION:
            if (startTime == null) {
                startTime = Long.valueOf(System.nanoTime());
            }
            break;
        case REQUEST_MATCHED:
            if (startTime == null) {
                startTime = Long.valueOf(System.nanoTime());
            }
            timerConfigs(event, true).stream().forEach((config) -> {
                if (config.getName() == null) {
                    log.warning("Unable to perform metrics timing for request '"
                            + event.getUriInfo().getRequestUri()
                            + "': @Timed annotation must have a value used to name the metric");
                    return;
                }
                longTaskTimerIds.put(event.getContainerRequest(),
                        longTaskTimer(event, config).start());
            });
            break;
        case FINISHED:
            if (startTime != null) {
                final long duration = System.nanoTime() - startTime.longValue();

                // time the request
                timerConfigs(event, false).forEach((config) -> {
                    final Builder builder = timerBuilder(event, config);
                    builder.register(meterRegistry).record(duration, TimeUnit.NANOSECONDS);
                });

                // time any long running task
                timerConfigs(event, true).stream().forEach((config) -> {
                    final Long timerId = longTaskTimerIds.remove(event.getContainerRequest());
                    if (timerId != null) {
                        final LongTaskTimer longTaskTimer = longTaskTimer(event, config);
                        longTaskTimer.stop(timerId);
                    }
                });
            }
            break;
        default:
            break;
        }
    }

    private Set<TimerConfig> timerConfigs(RequestEvent event, boolean selectLongTasks) {
        final Set<Timed> annotations = annotations(event, selectLongTasks);
        final Set<TimerConfig> timerConfigs = annotations.stream().map(a -> timerConfig(a))
                .collect(Collectors.toSet());

        /*
         * Given we didn't find any matching resource method, 404s will be only
         * recorded when auto-time-requests is enabled. On par with WebMVC
         * instrumentation.
         */
        if (timerConfigs.isEmpty() && autoTimeRequests) {
            timerConfigs.add(new TimerConfig(metricName, recordRequestPercentiles));
        }

        return timerConfigs;
    }

    private Set<Timed> annotations(RequestEvent event, boolean selectLongTasks) {
        // TODO: @Timed defined at resource class level
        final Set<Timed> timed = new HashSet<>();

        final ResourceMethod matchingResourceMethod = event.getUriInfo().getMatchedResourceMethod();
        if (matchingResourceMethod != null) {
            final Timed[] methodAnnotations = matchingResourceMethod.getInvocable()
                    .getHandlingMethod().getAnnotationsByType(Timed.class);
            if (methodAnnotations != null) {
                timed.addAll(Arrays.asList(methodAnnotations));
            }
        }
        return timed.stream().filter(a -> a.longTask() == selectLongTasks)
                .collect(Collectors.toSet());
    }

    private TimerConfig timerConfig(Timed annotation) {
        return new TimerConfig(annotation, () -> metricName);
    }

    private Timer.Builder timerBuilder(RequestEvent event, TimerConfig config) {
        final Timer.Builder builder = Timer.builder(config.getName())
                .tags(this.tagsProvider.httpRequestTags(event)).tags(config.getExtraTags())
                .description("Timer of servlet request")
                .publishPercentileHistogram(config.isHistogram());
        if (config.getPercentiles().length > 0) {
            builder.publishPercentiles(config.getPercentiles());
        }
        return builder;
    }

    private LongTaskTimer longTaskTimer(RequestEvent event, TimerConfig config) {
        return LongTaskTimer.builder(config.getName())
                .tags(this.tagsProvider.httpLongRequestTags(event)).tags(config.getExtraTags())
                .description("Timer of long servlet request").register(meterRegistry);
    }

    /**
     * An adjusted copy from WebMvcMetrics. (Move to core?)
     */
    private static class TimerConfig {

        private final String name;

        private final Iterable<Tag> extraTags;

        private final double[] percentiles;

        private final boolean histogram;

        TimerConfig(String name, boolean histogram) {
            this.name = name;
            this.extraTags = Collections.emptyList();
            this.percentiles = new double[0];
            this.histogram = histogram;
        }

        TimerConfig(Timed timed, Supplier<String> name) {
            this.name = buildName(timed, name);
            this.extraTags = Tags.zip(timed.extraTags());
            this.percentiles = timed.percentiles();
            this.histogram = timed.histogram();
        }

        private String buildName(Timed timed, Supplier<String> nameSupplier) {
            if (timed.longTask() && timed.value().isEmpty()) {
                // the user MUST name long task timers, we don't lump them in
                // with regular timers with the same name
                return null;
            }
            return (timed.value().isEmpty() ? nameSupplier.get() : timed.value());
        }

        public String getName() {
            return this.name;
        }

        Iterable<Tag> getExtraTags() {
            return this.extraTags;
        }

        double[] getPercentiles() {
            return this.percentiles;
        }

        boolean isHistogram() {
            return this.histogram;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TimerConfig other = (TimerConfig) o;
            return Objects.equals(this.name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.name);
        }

    }

}
