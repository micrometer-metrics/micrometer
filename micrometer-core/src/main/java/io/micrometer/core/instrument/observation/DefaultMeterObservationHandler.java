/*
 * Copyright 2024 VMware, Inc.
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

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.*;
import io.micrometer.observation.Observation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private final boolean shouldCreateLongTaskTimer;

    /**
     * Creates the handler with the default configuration.
     * @param meterRegistry the MeterRegistry to use
     */
    public DefaultMeterObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.shouldCreateLongTaskTimer = true;
    }

    /**
     * Creates the handler with the defined IgnoredMeters to use when the handler
     * processes the Observations.
     * @param meterRegistry the MeterRegistry to use
     * @param metersToIgnore the Meters that should not be created when Observations are
     * handled
     * @since 1.13.0
     */
    public DefaultMeterObservationHandler(MeterRegistry meterRegistry, IgnoredMeters... metersToIgnore) {
        this.meterRegistry = meterRegistry;
        this.shouldCreateLongTaskTimer = Arrays.stream(metersToIgnore)
            .noneMatch(ignored -> ignored == IgnoredMeters.LONG_TASK_TIMER);
    }

    @Override
    public void onStart(Observation.Context context) {
        if (shouldCreateLongTaskTimer) {
            LongTaskTimer.Sample longTaskSample = LongTaskTimer.builder(context.getName() + ".active")
                .tags(createTags(context))
                .register(meterRegistry)
                .start();
            context.put(LongTaskTimer.Sample.class, longTaskSample);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        context.put(Timer.Sample.class, sample);
    }

    @Override
    public void onStop(Observation.Context context) {
        List<Tag> tags = createTags(context);
        tags.add(Tag.of("error", getErrorValue(context)));
        Timer.Sample sample = context.getRequired(Timer.Sample.class);
        sample.stop(Timer.builder(context.getName()).tags(tags).register(this.meterRegistry));

        if (shouldCreateLongTaskTimer) {
            LongTaskTimer.Sample longTaskSample = context.getRequired(LongTaskTimer.Sample.class);
            longTaskSample.stop();
        }
    }

    @Override
    public void onEvent(Observation.Event event, Observation.Context context) {
        Counter.builder(context.getName() + "." + event.getName())
            .tags(createTags(context))
            .register(meterRegistry)
            .increment();
    }

    private String getErrorValue(Observation.Context context) {
        Throwable error = context.getError();
        return error != null ? error.getClass().getSimpleName() : "none";
    }

    private List<Tag> createTags(Observation.Context context) {
        List<Tag> tags = new ArrayList<>();
        for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
            tags.add(Tag.of(keyValue.getKey(), keyValue.getValue()));
        }
        return tags;
    }

    /**
     * Meter types to ignore.
     *
     * @since 1.13.0
     */
    public enum IgnoredMeters {

        LONG_TASK_TIMER

    }

}
