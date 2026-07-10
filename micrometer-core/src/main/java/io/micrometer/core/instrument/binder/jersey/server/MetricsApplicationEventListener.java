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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.core.instrument.MeterRegistry;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import static java.util.Objects.requireNonNull;

/**
 * The Micrometer {@link ApplicationEventListener} which registers
 * {@link RequestEventListener} for instrumenting Jersey server requests.
 *
 * @author Michael Weirauch
 * @since 1.8.0
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public class MetricsApplicationEventListener implements ApplicationEventListener {

    private final MeterRegistry meterRegistry;

    private final JerseyTagsProvider tagsProvider;

    private final String metricName;

    private final AnnotationFinder annotationFinder;

    private final boolean autoTimeRequests;

    public MetricsApplicationEventListener(MeterRegistry registry, JerseyTagsProvider tagsProvider, String metricName,
            boolean autoTimeRequests) {
        this(registry, tagsProvider, metricName, autoTimeRequests, AnnotationFinder.DEFAULT);
    }

    public MetricsApplicationEventListener(MeterRegistry registry, JerseyTagsProvider tagsProvider, String metricName,
            boolean autoTimeRequests, AnnotationFinder annotationFinder) {
        this.meterRegistry = requireNonNull(registry);
        this.tagsProvider = requireNonNull(tagsProvider);
        this.metricName = requireNonNull(metricName);
        this.annotationFinder = requireNonNull(annotationFinder);
        this.autoTimeRequests = autoTimeRequests;
    }

    @Override
    public void onEvent(ApplicationEvent event) {
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return new MetricsRequestEventListener(meterRegistry, tagsProvider, metricName, autoTimeRequests,
                annotationFinder);
    }

}
