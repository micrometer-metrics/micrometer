package io.micrometer.api.instrument;

import java.time.Duration;
import java.util.Collections;

import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.lang.Nullable;

public class NoopObservation implements Observation {
    private static final Iterable<Tag> TAGS = Collections.emptyList();
    public static final NoopObservation INSTANCE = new NoopObservation();

    private NoopObservation() {
    }

    @Override
    public Observation displayName(String displayName) {
        return this;
    }

    @Override
    public Observation lowCardinalityTag(Tag tag) {
        return this;
    }

    @Override
    public Observation lowCardinalityTag(String key, String value) {
        return this;
    }

    @Override
    public Observation highCardinalityTag(Tag tag) {
        return this;
    }

    @Override
    public Observation highCardinalityTag(String key, String value) {
        return this;
    }

    @Override
    public Observation error(Throwable error) {
        return this;
    }

    @Override
    public Observation start() {
        return this;
    }

    @Override
    public void stop() {
    }

    @Override
    public Scope openScope() {
        return NoOpScope.INSTANCE;
    }

    static class NoOpScope implements Scope {
        static final NoOpScope INSTANCE =  new NoOpScope();

        @Nullable
        @Override
        public Observation getCurrentObservation() {
            return NoopObservation.INSTANCE;
        }

        @Override
        public void close() {
        }
    }
}
