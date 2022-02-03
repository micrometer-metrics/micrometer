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

import io.micrometer.api.annotation.Incubating;
import io.micrometer.api.instrument.DistributionSummary;
import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Timer;
import io.micrometer.api.instrument.config.MeterFilter;
import io.micrometer.api.instrument.internal.OnlyOnceLoggingDenyMeterFilter;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;

import java.util.Optional;

/**
 * Provides request metrics for Jetty {@link org.eclipse.jetty.client.HttpClient},
 * configured as a {@link org.eclipse.jetty.client.api.Request.Listener Request.Listener}.
 * Incubating in case there emerges a better way to handle path variable detection.
 *
 * @author Jon Schneider
 * @since 1.5.0
 */
@Incubating(since = "1.5.0")
public class JettyClientMetrics implements Request.Listener {
    private final MeterRegistry registry;
    private final JettyClientTagsProvider tagsProvider;
    private final String timingMetricName;
    private final String contentSizeMetricName;

    protected JettyClientMetrics(MeterRegistry registry, JettyClientTagsProvider tagsProvider, String timingMetricName, String contentSizeMetricName, int maxUriTags) {
        this.registry = registry;
        this.tagsProvider = tagsProvider;
        this.timingMetricName = timingMetricName;
        this.contentSizeMetricName = contentSizeMetricName;

        MeterFilter timingMetricDenyFilter = new OnlyOnceLoggingDenyMeterFilter(
                () -> String.format("Reached the maximum number of URI tags for '%s'.", timingMetricName));
        MeterFilter contentSizeMetricDenyFilter = new OnlyOnceLoggingDenyMeterFilter(
                () -> String.format("Reached the maximum number of URI tags for '%s'.", contentSizeMetricName));
        registry.config()
                .meterFilter(MeterFilter.maximumAllowableTags(
                        this.timingMetricName, "uri", maxUriTags, timingMetricDenyFilter))
                .meterFilter(MeterFilter.maximumAllowableTags(
                        this.contentSizeMetricName, "uri", maxUriTags, contentSizeMetricDenyFilter));
    }

    @Override
    public void onQueued(Request request) {
        Timer.Sample sample = Timer.start(registry);

        request.onComplete(result -> {
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

            sample.stop(Timer.builder(timingMetricName)
                    .description("Jetty HTTP client request timing")
                    .tags(httpRequestTags)
                    .register(registry)
            );
        });
    }

    public static Builder builder(MeterRegistry registry, JettyClientTagsProvider tagsProvider) {
        return new Builder(registry, tagsProvider);
    }

    public static class Builder {
        private final MeterRegistry registry;
        private final JettyClientTagsProvider tagsProvider;

        private String timingMetricName = "jetty.client.requests";
        private String contentSizeMetricName = "jetty.client.request.size";
        private int maxUriTags = 1000;

        Builder(MeterRegistry registry, JettyClientTagsProvider tagsProvider) {
            this.registry = registry;
            this.tagsProvider = tagsProvider;
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

        public JettyClientMetrics build() {
            return new JettyClientMetrics(registry, tagsProvider, timingMetricName, contentSizeMetricName, maxUriTags);
        }
    }
}
