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
 * <p>
 * Use {@link #builder(MeterRegistry)} to create an instance. The builder does
 * <em>not</em> create the active observation {@link LongTaskTimer} (named
 * {@code <observation-name>.active}); opt in to it with
 * {@link Builder#includeActiveObservationLongTaskTimer(boolean)}. The deprecated
 * constructors always create it.
 * <p>
 * This class is not designed for extension. To customize behavior, implement
 * {@link MeterObservationHandler} and delegate to an instance created by
 * {@link #builder(MeterRegistry)}; every {@code ObservationHandler} lifecycle method has
 * a no-op default, so only the methods you care about need to be delegated.
 * <p>
 * WARNING: When the active observation {@link LongTaskTimer} is included: since it needs
 * to be created in the {@code onStart} method, it can only contain tags that are
 * available by that time. This means that if you add a {@code lowCardinalityKeyValue}
 * after calling {@code start} on the {@link Observation}, that {@code KeyValue} will not
 * be translated as a {@link Tag} on the {@link LongTaskTimer}. Likewise, since the
 * {@code KeyValuesProvider} is evaluated in the {@code stop} method of the
 * {@link Observation} (after start), those {@code KeyValue} instances will not be used
 * for the {@link LongTaskTimer}.
 *
 * @author Marcin Grzejszczak
 * @author Tommy Ludwig
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
public class DefaultMeterObservationHandler implements MeterObservationHandler<Observation.Context> {

    private final MeterRegistry meterRegistry;

    private final boolean includeActiveObservationLongTaskTimer;

    /**
     * Creates the handler with the default configuration.
     * @param meterRegistry the MeterRegistry to use
     * @deprecated since 1.18.0, use {@link #builder(MeterRegistry)} instead. Beware that
     * this constructor creates a {@link LongTaskTimer} named
     * {@code <observation-name>.active} in addition to the {@link Timer}, but
     * {@code builder(meterRegistry).build()} does not. Use
     * {@code builder(meterRegistry).includeActiveObservationLongTaskTimer(true).build()}
     * to keep the behavior of this constructor.
     */
    @Deprecated
    public DefaultMeterObservationHandler(MeterRegistry meterRegistry) {
        this(builder(meterRegistry).includeActiveObservationLongTaskTimer(true));
    }

    /**
     * Creates the handler with the defined IgnoredMeters to use when the handler
     * processes the Observations.
     * @param meterRegistry the MeterRegistry to use
     * @param metersToIgnore the Meters that should not be created when Observations are
     * handled
     * @since 1.13.0
     * @deprecated since 1.18.0, use {@link #builder(MeterRegistry)} and
     * {@link Builder#includeActiveObservationLongTaskTimer(boolean)} instead. Opting
     * <em>out</em> by meter type does not scale as meters are added and cannot express a
     * different default. {@code new DefaultMeterObservationHandler(registry)} maps to
     * {@code builder(registry).includeActiveObservationLongTaskTimer(true).build()}, and
     * {@code new DefaultMeterObservationHandler(registry, IgnoredMeters.LONG_TASK_TIMER)}
     * maps to {@code builder(registry).build()}.
     */
    @Deprecated
    public DefaultMeterObservationHandler(MeterRegistry meterRegistry, IgnoredMeters... metersToIgnore) {
        this(builder(meterRegistry).includeActiveObservationLongTaskTimer(
                Arrays.stream(metersToIgnore).noneMatch(ignored -> ignored == IgnoredMeters.LONG_TASK_TIMER)));
    }

    DefaultMeterObservationHandler(Builder builder) {
        this.meterRegistry = builder.meterRegistry;
        this.includeActiveObservationLongTaskTimer = builder.includeActiveObservationLongTaskTimer;
    }

    /**
     * Returns a {@link Builder} for a {@code DefaultMeterObservationHandler}. The
     * returned builder does <em>not</em> create the active observation
     * {@link LongTaskTimer} (named {@code <observation-name>.active}) unless
     * {@link Builder#includeActiveObservationLongTaskTimer(boolean)} is set to
     * {@code true}. This differs from the deprecated constructors, which always create
     * it.
     * @param meterRegistry the MeterRegistry in which to register meters
     * @return a new builder
     * @since 1.18.0
     */
    public static Builder builder(MeterRegistry meterRegistry) {
        return new Builder(meterRegistry);
    }

    @Override
    public void onStart(Observation.Context context) {
        if (includeActiveObservationLongTaskTimer) {
            LongTaskTimer.Sample longTaskSample = meterRegistry.more()
                .longTaskTimer(context.getName() + ".active", createTags(context))
                .start();
            context.put(LongTaskTimer.Sample.class, longTaskSample);
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        context.put(Timer.Sample.class, sample);
    }

    @Override
    // TODO decide what to do about context.getName being Nullable
    @SuppressWarnings("NullAway")
    public void onStop(Observation.Context context) {
        List<Tag> tags = createTags(context);
        tags.add(Tag.of("error", getErrorValue(context)));
        Timer.Sample sample = context.getRequired(Timer.Sample.class);
        sample.stop(this.meterRegistry.timer(context.getName(), tags));

        if (includeActiveObservationLongTaskTimer) {
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
        return error != null ? error.getClass().getSimpleName() : KeyValue.NONE_VALUE;
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
     * @deprecated since 1.18.0 with no direct replacement. This enum names a <em>meter
     * type</em> rather than a specific meter this handler creates, and its opt-out
     * semantics cannot express a different default. Configure meters individually with
     * {@link #builder(MeterRegistry)}, for example
     * {@link Builder#includeActiveObservationLongTaskTimer(boolean)}.
     */
    @Deprecated
    public enum IgnoredMeters {

        LONG_TASK_TIMER

    }

    /**
     * Builder for {@code DefaultMeterObservationHandler}.
     *
     * @since 1.18.0
     */
    public static class Builder {

        private final MeterRegistry meterRegistry;

        private boolean includeActiveObservationLongTaskTimer;

        Builder(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
        }

        /**
         * Whether to create the active observation {@link LongTaskTimer} (named
         * {@code <observation-name>.active}), which tracks the duration of in-progress
         * Observations, in addition to the {@link Timer} recorded when an Observation is
         * stopped. Defaults to {@code false}.
         * <p>
         * The deprecated {@code DefaultMeterObservationHandler} constructors always
         * create this {@link LongTaskTimer}; set this to {@code true} to keep that
         * behavior when migrating to this builder.
         * @param includeActiveObservationLongTaskTimer whether to create the active
         * observation {@link LongTaskTimer}
         * @return this builder
         */
        public Builder includeActiveObservationLongTaskTimer(boolean includeActiveObservationLongTaskTimer) {
            this.includeActiveObservationLongTaskTimer = includeActiveObservationLongTaskTimer;
            return this;
        }

        /**
         * Build a {@code DefaultMeterObservationHandler}.
         * @return a new {@code DefaultMeterObservationHandler}
         */
        public DefaultMeterObservationHandler build() {
            return new DefaultMeterObservationHandler(this);
        }

    }

}
