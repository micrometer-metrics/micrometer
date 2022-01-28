package io.micrometer.api.instrument;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import io.micrometer.api.lang.Nullable;

public interface Observation {

    String getName();

    default String getDescription() {
        return "";
    }

    default Observation description(String description) {
        return this;
    }

    String getDisplayName();

    Observation displayName(String displayName);

    Iterable<Tag> getLowCardinalityTags();

    Observation lowCardinalityTag(Tag tag);

    default Observation lowCardinalityTag(String key, String value) {
        return lowCardinalityTag(Tag.of(key, value));
    }

    Iterable<Tag> getHighCardinalityTags();

    Observation highCardinalityTag(Tag tag);

    default Observation highCardinalityTag(String key, String value) {
        return highCardinalityTag(Tag.of(key, value));
    }

    @Nullable Throwable getError();

    Observation error(Throwable error);

    Duration getDuration();

    long getStartNanos();

    long getStopNanos();

    long getStartWallTime();

    Observation start();

    void stop();

    Scope openScope();

    interface Scope extends AutoCloseable {
        @Nullable Observation getCurrentObservation();

        @Override void close();
    }

    @SuppressWarnings("unchecked")
    class Context implements TagsProvider {
        private final Map<Class<?>, Object> map = new HashMap<>();

        public <T> Context put(Class<T> clazz, T object) {
            this.map.put(clazz, object);
            return this;
        }

        public void remove(Class<?> clazz) {
            this.map.remove(clazz);
        }

        @Nullable public <T> T get(Class<T> clazz) {
            return (T) this.map.get(clazz);
        }

        public <T> T getOrDefault(Class<T> clazz, T defaultObject) {
            return (T) this.map.getOrDefault(clazz, defaultObject);
        }

        public <T> T computeIfAbsent(Class<T> clazz, Function<Class<?>, ? extends T> mappingFunction) {
            return (T) this.map.computeIfAbsent(clazz, mappingFunction);
        }

        public void clear() {
            this.map.clear();
        }
    }
}
