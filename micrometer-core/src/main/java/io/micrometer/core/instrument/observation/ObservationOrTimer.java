/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.core.instrument.observation;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.RequestReplySenderContext;

import java.util.function.Supplier;

/**
 * @param <T> context type if Observation used for instrumentation
 */
public class ObservationOrTimer<T extends Observation.Context> {

    private final MeterRegistry meterRegistry;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final Observation.ObservationConvention<T> convention;

    private final Observation.ObservationConvention<T> defaultConvention;

    @Nullable
    private Timer.Sample sample;

    @Nullable
    private Observation observation;

    @Nullable
    private T context;

    public static <T extends Observation.Context> ObservationOrTimer<T> start(MeterRegistry meterRegistry,
            ObservationRegistry observationRegistry, Supplier<T> context,
            @Nullable Observation.ObservationConvention<T> convention,
            Observation.ObservationConvention<T> defaultConvention) {
        ObservationOrTimer<T> observationOrTimer = new ObservationOrTimer<>(meterRegistry, observationRegistry, context,
                convention, defaultConvention);
        observationOrTimer.start(context);
        return observationOrTimer;
    }

    private ObservationOrTimer(MeterRegistry meterRegistry, ObservationRegistry observationRegistry,
            Supplier<T> context, @Nullable Observation.ObservationConvention<T> convention,
            Observation.ObservationConvention<T> defaultConvention) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry;
        this.convention = convention;
        this.defaultConvention = defaultConvention;
    }

    private void start(Supplier<T> contextSupplier) {
        if (observationRegistry.isNoop()) {
            sample = Timer.start(meterRegistry);
        }
        else {
            this.context = contextSupplier.get();
            observation = Observation.start(convention, defaultConvention, context, observationRegistry);
        }
    }

    /**
     * If using an Observation for instrumentation and the context is a
     * {@link RequestReplySenderContext}, set the response object on it.
     * @param response response for the RequestReplySenderContext
     * @param <RES> type of the response
     */
    public <RES> void setResponse(RES response) {
        if (observationRegistry.isNoop() || !(context instanceof RequestReplySenderContext)) {
            return;
        }
        RequestReplySenderContext<?, RES> requestReplySenderContext = (RequestReplySenderContext<?, RES>) context;
        requestReplySenderContext.setResponse(response);
    }

    /**
     * Stop the timing. The tags that should be passed to the timer need to be passed
     * here. These will only be used if an Observation
     * @param tagsSupplier tags supplier to apply if using the Timer API
     */
    public void stop(String timerName, @Nullable String description, Supplier<Iterable<Tag>> tagsSupplier) {
        if (observationRegistry.isNoop() && sample != null) {
            sample.stop(
                    Timer.builder(timerName).description(description).tags(tagsSupplier.get()).register(meterRegistry));
        }
        else if (observation != null) {
            observation.stop();
        }
    }

}
