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

import java.util.concurrent.TimeUnit;

import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Micrometer {@link RequestEventListener} recording metrics for Jersey server
 * requests.
 * 
 * @author Michael Weirauch
 */
public class MicrometerRequestEventListener implements RequestEventListener {

    private final MeterRegistry meterRegistry;

    private final JerseyTagsProvider tagsProvider;

    private final String metricName;

    private Long startTime;

    public MicrometerRequestEventListener(MeterRegistry meterRegistry,
            JerseyTagsProvider tagsProvider, String metricName) {
        this.meterRegistry = requireNonNull(meterRegistry);
        this.tagsProvider = requireNonNull(tagsProvider);
        this.metricName = requireNonNull(metricName);
    }

    @Override
    public void onEvent(RequestEvent event) {
        switch (event.getType()) {
        case ON_EXCEPTION:
            startTime = Long.valueOf(System.nanoTime());
            break;
        case REQUEST_MATCHED:
            if (startTime == null) {
                startTime = Long.valueOf(System.nanoTime());
            }
            break;
        case FINISHED:
            if (startTime != null) {
                final long duration = System.nanoTime() - startTime.longValue();

                final Timer timer = Timer.builder(metricName)
                        .tags(tagsProvider.httpRequestTags(event)).register(meterRegistry);
                timer.record(duration, TimeUnit.NANOSECONDS);
            }
            break;
        default:
            break;
        }
    }

}
