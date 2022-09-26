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
 * Handler for {@link Timer.Sample} and {@link Counter}.
 *
 * WARNING: Since the {@link LongTaskTimer} needs to be created in the {@code onStart}
 * method, it can only contain tags that are available by that time. This means that if
 * you add a {@code lowCardinalityKeyValue} after calling {@code start} on the
 * {@link Observation}, that {@code KeyValue} will not be translated as a {@link Tag} on
 * the {@link LongTaskTimer}. Likewise, since the {@code KeyValuesProvider} is evaluated
 * in the {@code stop} method of the {@link Observation} (after start), those
 * {@code KeyValue} instances will not be used for the {@link LongTaskTimer}.
 *
 * @author Marcin Grzejszczak
 * @author Tommy Ludwig
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
public class DefaultMeterObservationHandler implements MeterObservationHandler<Observation.Context> {

    private final MeterRegistry meterRegistry;

    public DefaultMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onStart(Observation.Context context) {
        LongTaskTimer.Sample longTaskSample = LongTaskTimer.builder(context.getName() + ".active")
                .tags(createTags(context)).register(meterRegistry).start();
        context.put(LongTaskTimer.Sample.class, longTaskSample);

        Timer.Sample sample = Timer.start(meterRegistry);
        context.put(Timer.Sample.class, sample);
    }

    @Override
    public void onStop(Observation.Context context) {
        Timer.Sample sample = context.getRequired(Timer.Sample.class);
        sample.stop(Timer.builder(context.getName()).tags(createErrorTags(context)).tags(createTags(context))
                .register(this.meterRegistry));

        LongTaskTimer.Sample longTaskSample = context.getRequired(LongTaskTimer.Sample.class);
        longTaskSample.stop();
    }

    @Override
    public void onEvent(Observation.Event event, Observation.Context context) {
        Counter.builder(context.getName() + "." + event.getName()).tags(createTags(context)).register(meterRegistry)
                .increment();
    }

    private Tags createErrorTags(Observation.Context context) {
        return Tags.of("error", getErrorValue(context));
    }

    private String getErrorValue(Observation.Context context) {
        Throwable error = context.getError();
        return error != null ? error.getClass().getSimpleName() : "none";
    }

    private Tags createTags(Observation.Context context) {
        return Tags.of(context.getLowCardinalityKeyValues().stream().map(tag -> Tag.of(tag.getKey(), tag.getValue()))
                .collect(Collectors.toList()));
    }

}
