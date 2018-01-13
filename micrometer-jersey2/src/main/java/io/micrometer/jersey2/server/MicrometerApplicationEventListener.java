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

import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The Micrometer {@link ApplicationEventListener} which registers
 * {@link RequestEventListener} for instrumenting Jersey server requests.
 * 
 * @author Michael Weirauch
 */
public class MicrometerApplicationEventListener implements ApplicationEventListener {

    private final MeterRegistry meterRegistry;

    private final JerseyTagsProvider tagsProvider;

    private final String metricName;

    private final boolean autoTimeRequests;

    private final boolean recordRequestPercentiles;

    public MicrometerApplicationEventListener(MeterRegistry meterRegistry,
            JerseyTagsProvider tagsProvider, String metricName, boolean autoTimeRequests,
            boolean recordRequestPercentiles) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.tagsProvider = requireNonNull(tagsProvider);
        this.metricName = requireNonNull(metricName);
        this.autoTimeRequests = autoTimeRequests;
        this.recordRequestPercentiles = recordRequestPercentiles;
    }

    @Override
    public void onEvent(ApplicationEvent event) {
        //
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return new MicrometerRequestEventListener(meterRegistry, tagsProvider, metricName,
                autoTimeRequests, recordRequestPercentiles);
    }

}
