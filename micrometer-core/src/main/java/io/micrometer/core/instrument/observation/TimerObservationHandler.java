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

import io.micrometer.core.instrument.*;
import io.micrometer.observation.Observation;

import java.util.stream.Collectors;

/**
 * Handler for {@link Timer.Sample}.
 *
 * @author Marcin Grzejszczak
 * @since 1.10.0
 */
public class TimerObservationHandler implements MeterObservationHandler<Observation.Context> {

    private final MeterRegistry meterRegistry;

    public TimerObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(Observation.Context context) {
        if (context.isLongTask()) {
            LongTaskTimer.Sample longTaskSample = LongTaskTimer.builder(context.getName() + ".active")
                    .tags(createTags(context)).register(meterRegistry).start();
            context.put(LongTaskTimer.Sample.class, longTaskSample);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        context.put(Timer.Sample.class, sample);
    }

    @Override
    public void onStop(Observation.Context context) {
        Timer.Sample sample = context.getRequired(Timer.Sample.class);
        sample.stop(Timer.builder(context.getName()).tags(createErrorTags(context)).tags(createTags(context))
                .register(this.meterRegistry));

        if (context.isLongTask()) {
            LongTaskTimer.Sample longTaskSample = context.getRequired(LongTaskTimer.Sample.class);
            longTaskSample.stop();
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    private Tags createErrorTags(Observation.Context context) {
        return Tags.of("error",
                context.getError().map(throwable -> throwable.getClass().getSimpleName()).orElse("none"));
    }

    private Tags createTags(Observation.Context context) {
        return Tags.of(context.getLowCardinalityKeyValues().stream().map(tag -> Tag.of(tag.getKey(), tag.getValue()))
                .collect(Collectors.toList()));
    }

}
