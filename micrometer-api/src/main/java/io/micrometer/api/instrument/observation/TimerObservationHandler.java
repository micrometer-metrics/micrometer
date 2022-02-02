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
package io.micrometer.api.instrument.observation;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Timer;

/**
 * Handler for {@link Timer.Sample}.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TimerObservationHandler implements ObservationHandler<Observation.Context> {

    private final MeterRegistry meterRegistry;

    public TimerObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(Observation observation, Observation.Context context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        context.put(Timer.Sample.class, sample);
    }

    @Override
    public void onError(Observation observation, Observation.Context context) {
    }

    @Override
    public void onStop(Observation observation, Observation.Context context) {
        Timer.Sample sample = context.get(Timer.Sample.class);
        sample.stop(Timer.builder(context.getName())
                .tag("error", context.getError().map(Throwable::getMessage).orElse("none"))
                .tags(context.getLowCardinalityTags().and(context.getAdditionalLowCardinalityTags()))
                .register(this.meterRegistry));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }
}
