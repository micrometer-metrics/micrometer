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
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.transport.ResponseContext;

import java.util.function.Supplier;

/**
 * Abstracts instrumenting code with a {@link Timer} or an {@link Observation}. This can
 * be useful for avoiding complexity and minimizing overhead when converting
 * instrumentation that was previously instrumented with a {@link Timer} to being
 * optionally instrumented with an {@link Observation}. This may be necessary where
 * backwards compatibility is a concern such that you cannot require an
 * {@link ObservationRegistry}. If there are no backwards compatibility concerns,
 * generally direct instrumentation with {@link Observation} should be preferred. While
 * this was designed for use internally in micrometer-core where we have this need, it may
 * also be useful to other libraries with pre-existing {@link Timer}-based
 * instrumentation. If an {@link ObservationRegistry} is provided that is not the no-op
 * registry, an {@link Observation} will be used for instrumentation. Otherwise, a
 * {@link Timer} will be used.
 *
 * @param <T> context type if Observation used for instrumentation
 * @since 1.10.0
 */
public class ObservationOrTimerCompatibleInstrumentation<T extends Observation.Context> {

    private final MeterRegistry meterRegistry;

    private final ObservationRegistry observationRegistry;

    @Nullable
    private final ObservationConvention<T> convention;

    private final ObservationConvention<T> defaultConvention;

    @Nullable
    private Timer.Sample timerSample;

    @Nullable
    private Observation observation;

    @Nullable
    private T context;

    @Nullable
    private Throwable throwable;

    /**
     * Start timing based on Observation and the convention for it if
     * {@link ObservationRegistry} is not null and not the no-op registry. Otherwise,
     * timing will be instrumented with a {@link Timer} using the {@link MeterRegistry}.
     * @param meterRegistry registry for Timer-based instrumentation
     * @param observationRegistry registry for Observation-based instrumentation
     * @param context supplier for the context to use if instrumenting with Observation
     * @param convention convention that overrides the default convention and any
     * conventions configured on the registry, if not null
     * @param defaultConvention convention to use if one is not configured
     * @return a started instrumentation
     * @param <T> context type if Observation used for instrumentation
     */
    public static <T extends Observation.Context> ObservationOrTimerCompatibleInstrumentation<T> start(
            MeterRegistry meterRegistry, @Nullable ObservationRegistry observationRegistry, Supplier<T> context,
            @Nullable ObservationConvention<T> convention, ObservationConvention<T> defaultConvention) {
        ObservationOrTimerCompatibleInstrumentation<T> observationOrTimer = new ObservationOrTimerCompatibleInstrumentation<>(
                meterRegistry, observationRegistry, convention, defaultConvention);
        observationOrTimer.start(context);
        return observationOrTimer;
    }

    private ObservationOrTimerCompatibleInstrumentation(MeterRegistry meterRegistry,
            @Nullable ObservationRegistry observationRegistry, @Nullable ObservationConvention<T> convention,
            ObservationConvention<T> defaultConvention) {
        this.meterRegistry = meterRegistry;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        this.convention = convention;
        this.defaultConvention = defaultConvention;
    }

    @SuppressWarnings("unchecked")
    private void start(Supplier<T> contextSupplier) {
        if (observationRegistry.isNoop()) {
            timerSample = Timer.start(meterRegistry);
        }
        else {
            observation = Observation.start(convention, defaultConvention, contextSupplier, observationRegistry);
            context = (T) observation.getContext();
        }
    }

    /**
     * If using an Observation for instrumentation and the context is a
     * {@link ResponseContext}, set the response object on it. Otherwise, do nothing.
     * @param response response for the RequestReplySenderContext
     * @param <RES> type of the response
     */
    public <RES> void setResponse(RES response) {
        if (observationRegistry.isNoop() || !(context instanceof ResponseContext)) {
            return;
        }
        @SuppressWarnings("unchecked")
        ResponseContext<? super RES> responseContext = (ResponseContext<? super RES>) context;
        responseContext.setResponse(response);
    }

    /**
     * If using an Observation, it will set the error on Observation. For metrics, it will
     * do nothing.
     * @param throwable error that got recorded
     */
    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    /**
     * Stop the timing. The tags that should be applied to the timer need to be passed
     * here. These parameters will only be used if instrumentation is Timer-based.
     * Observation-based instrumentation will use tags and the name from the applicable
     * convention.
     * @param timerName name of the timer if instrumentation is done with Timer
     * @param timerDescription description for the timer
     * @param tagsSupplier tags supplier to apply if using the Timer API
     */
    public void stop(String timerName, @Nullable String timerDescription, Supplier<Iterable<Tag>> tagsSupplier) {
        if (observationRegistry.isNoop() && timerSample != null) {
            timerSample.stop(Timer.builder(timerName)
                .description(timerDescription)
                .tags(tagsSupplier.get())
                .register(meterRegistry));
        }
        else if (observation != null) {
            if (throwable != null) {
                observation.error(throwable);
            }
            observation.stop();
        }
    }

}
