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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.internal.OnlyOnceLoggingDenyMeterFilter;
import io.micrometer.core.instrument.observation.ObservationOrTimerCompatibleInstrumentation;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Provides request metrics for Jetty {@link org.eclipse.jetty.client.HttpClient},
 * configured as a {@link org.eclipse.jetty.client.api.Request.Listener Request.Listener}.
 * Incubating in case there emerges a better way to handle path variable detection.
 *
 * @author Jon Schneider
 * @since 1.5.0
 * @deprecated since 1.13.0 in favor of the micrometer-jetty12 module
 */
@Deprecated
@Incubating(since = "1.5.0")
public class JettyClientMetrics implements Request.Listener {

    static final String DEFAULT_JETTY_CLIENT_REQUESTS_TIMER_NAME = "jetty.client.requests";

    private final MeterRegistry registry;

    private final JettyClientTagsProvider tagsProvider;

    private final String timingMetricName;

    private final String contentSizeMetricName;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final JettyClientObservationConvention convention;

    private final BiFunction<Request, Result, String> uriPatternFunction;

    /**
     * @deprecated since 1.11.0 in favor of
     * {@link JettyClientMetrics#builder(MeterRegistry, BiFunction)}
     */
    @Deprecated
    protected JettyClientMetrics(MeterRegistry registry, JettyClientTagsProvider tagsProvider, String timingMetricName,
            String contentSizeMetricName, int maxUriTags) {
        this(registry, ObservationRegistry.NOOP, null, tagsProvider, timingMetricName, contentSizeMetricName,
                maxUriTags, (request, result) -> tagsProvider.uriPattern(result));
    }

    private JettyClientMetrics(MeterRegistry registry, ObservationRegistry observationRegistry,
            @Nullable JettyClientObservationConvention convention, JettyClientTagsProvider tagsProvider,
            String timingMetricName, String contentSizeMetricName, int maxUriTags,
            BiFunction<Request, Result, String> uriPatternFunction) {
        this.registry = registry;
        this.tagsProvider = tagsProvider;
        this.timingMetricName = timingMetricName;
        this.contentSizeMetricName = contentSizeMetricName;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
        this.uriPatternFunction = uriPatternFunction;

        MeterFilter timingMetricDenyFilter = new OnlyOnceLoggingDenyMeterFilter(
                () -> String.format("Reached the maximum number of URI tags for '%s'.", timingMetricName));
        MeterFilter contentSizeMetricDenyFilter = new OnlyOnceLoggingDenyMeterFilter(
                () -> String.format("Reached the maximum number of URI tags for '%s'.", contentSizeMetricName));
        registry.config()
            .meterFilter(
                    MeterFilter.maximumAllowableTags(this.timingMetricName, "uri", maxUriTags, timingMetricDenyFilter))
            .meterFilter(MeterFilter.maximumAllowableTags(this.contentSizeMetricName, "uri", maxUriTags,
                    contentSizeMetricDenyFilter));
    }

    @Override
    public void onQueued(Request request) {
        ObservationOrTimerCompatibleInstrumentation<JettyClientContext> sample = ObservationOrTimerCompatibleInstrumentation
            .start(registry, observationRegistry, () -> new JettyClientContext(request, uriPatternFunction), convention,
                    DefaultJettyClientObservationConvention.INSTANCE);

        request.onComplete(result -> {
            sample.setResponse(result);
            long requestLength = Optional.ofNullable(result.getRequest().getContent())
                .map(ContentProvider::getLength)
                .orElse(0L);
            Iterable<Tag> httpRequestTags = tagsProvider.httpRequestTags(result);
            if (requestLength >= 0) {
                DistributionSummary.builder(contentSizeMetricName)
                    .description("Content sizes for Jetty HTTP client requests")
                    .tags(httpRequestTags)
                    .register(registry)
                    .record(requestLength);
            }

            sample.stop(timingMetricName, "Jetty HTTP client request timing", () -> httpRequestTags);
        });
    }

    /**
     * Create a builder for {@link JettyClientMetrics}.
     * @param registry meter registry to use
     * @param tagsProvider tags provider for customizing tagging
     * @return builder
     * @deprecated since 1.11.0 in favor of {@link #builder(MeterRegistry, BiFunction)};
     * {@link Builder#tagsProvider(JettyClientTagsProvider)} can be used to provide a
     * custom tags provider
     */
    @Deprecated
    public static Builder builder(MeterRegistry registry, JettyClientTagsProvider tagsProvider) {
        return new Builder(registry, (request, result) -> tagsProvider.uriPattern(result));
    }

    /**
     * Create a builder for {@link JettyClientMetrics}.
     * @param registry meter registry to use
     * @param uriPatternFunction how to extract the URI pattern for tagging
     * @return builder
     * @since 1.11.0
     */
    public static Builder builder(MeterRegistry registry, BiFunction<Request, Result, String> uriPatternFunction) {
        return new Builder(registry, uriPatternFunction);
    }

    public static class Builder {

        private final MeterRegistry meterRegistry;

        private final BiFunction<Request, Result, String> uriPatternFunction;

        private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

        private JettyClientTagsProvider tagsProvider;

        private String timingMetricName = DEFAULT_JETTY_CLIENT_REQUESTS_TIMER_NAME;

        private String contentSizeMetricName = "jetty.client.request.size";

        private int maxUriTags = 1000;

        @Nullable
        private JettyClientObservationConvention observationConvention;

        private Builder(MeterRegistry registry, BiFunction<Request, Result, String> uriPatternFunction) {
            this.meterRegistry = registry;
            this.uriPatternFunction = uriPatternFunction;
            this.tagsProvider = result -> uriPatternFunction.apply(result.getRequest(), result);
        }

        public Builder timingMetricName(String metricName) {
            this.timingMetricName = metricName;
            return this;
        }

        public Builder contentSizeMetricName(String metricName) {
            this.contentSizeMetricName = metricName;
            return this;
        }

        public Builder maxUriTags(int maxUriTags) {
            this.maxUriTags = maxUriTags;
            return this;
        }

        /**
         * Note that the {@link JettyClientTagsProvider} will not be used with
         * {@link Observation} instrumentation when
         * {@link #observationRegistry(ObservationRegistry)} is configured.
         * @param tagsProvider tags provider to use with metrics instrumentation
         * @return this builder
         * @since 1.11.0
         */
        public Builder tagsProvider(JettyClientTagsProvider tagsProvider) {
            this.tagsProvider = tagsProvider;
            return this;
        }

        /**
         * Configure an observation registry to instrument using the {@link Observation}
         * API instead of directly with a {@link Timer}.
         * @param observationRegistry registry with which to instrument
         * @return this builder
         * @since 1.11.0
         */
        public Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        /**
         * Provide a custom convention to override the default convention used when
         * instrumenting with the {@link Observation} API. This only takes effect when a
         * {@link #observationRegistry(ObservationRegistry)} is configured.
         * @param convention semantic convention to use
         * @return This builder instance.
         * @see #observationRegistry(ObservationRegistry)
         * @since 1.11.0
         */
        public Builder observationConvention(JettyClientObservationConvention convention) {
            this.observationConvention = convention;
            return this;
        }

        public JettyClientMetrics build() {
            return new JettyClientMetrics(meterRegistry, observationRegistry, observationConvention, tagsProvider,
                    timingMetricName, contentSizeMetricName, maxUriTags, uriPatternFunction);
        }

    }

}
