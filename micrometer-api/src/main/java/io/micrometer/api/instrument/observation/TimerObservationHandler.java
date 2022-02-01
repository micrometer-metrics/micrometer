package io.micrometer.api.instrument.observation;

import io.micrometer.api.instrument.MeterRegistry;
import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.Timer;

/**
 * Handler for {@link Timer.Sample}.
 *
 * @author Marcin Grzejszczak
 * @since 2.0.0
 */
public class TimerObservationHandler implements ObservationHandler {

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
    public void onError(Observation observation, Observation.Context context, Throwable throwable) {
        context.put(Throwable.class, throwable);
    }

    @Override
    public void onStop(Observation observation, Observation.Context context) {
        Timer.Sample sample = context.get(Timer.Sample.class);
        Tags tags = context.getLowCardinalityTags().and(context.getAdditionalLowCardinalityTags());
        if (context.containsKey(Throwable.class)) {
            tags = tags.and(Tag.of("error", context.get(Throwable.class).getLocalizedMessage()));
        }
        sample.stop(Timer.builder(context.getName())
                .tags(tags)
                .register(this.meterRegistry));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }
}
