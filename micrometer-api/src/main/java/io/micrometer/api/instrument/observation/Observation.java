package io.micrometer.api.instrument.observation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.micrometer.api.instrument.Tag;
import io.micrometer.api.instrument.Tags;
import io.micrometer.api.instrument.TagsProvider;
import io.micrometer.api.lang.Nullable;

/*

We moved out all the getters - this API will provide a nice way to set things ON THE CONTEXT
* We've added additionalLowCardinality and high cardinliaty tags on the context, tagsprovider is immutable
We remove timing information - there's no gain in unifying the time (e.g. same time for metrics & spans). It's up to the handlers
to take control of doing measurements. If a handler is buggy we will see that in its timing information.
We removed the throwable getter to explicitly pass the throwable to the handler - that's the only parameter that will never change (onError - you have to have an error there)
 */
public interface Observation {

    /**
     * Sets the display name (a more human-readable name).
     *
     * @param displayName display name
     * @return this
     */
    Observation displayName(String displayName);

    /**
     * Sets an additional low cardinality tag.
     *
     * @param tag tag
     * @return this
     */
    Observation lowCardinalityTag(Tag tag);

    /**
     * Sets an additional low cardinality tag.
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation lowCardinalityTag(String key, String value) {
        return lowCardinalityTag(Tag.of(key, value));
    }

    /**
     * Sets an additional high cardinality tag.
     *
     * @param tag tag
     * @return this
     */
    Observation highCardinalityTag(Tag tag);

    /**
     * Sets an additional high cardinality tag.
     *
     * @param key tag key
     * @param value tag value
     * @return this
     */
    default Observation highCardinalityTag(String key, String value) {
        return highCardinalityTag(Tag.of(key, value));
    }

    /**
     * Sets an error.
     *
     * @param error error
     * @return this
     */
    Observation error(Throwable error);

    /**
     * Starts the observation. Remember to call this method, otherwise
     * timing calculations will not take place.
     *
     * @return this
     */
    Observation start();

    /**
     * Stop the observation. Remember to call this method, otherwise
     * timing calculations won't be finished.
     */
    void stop();

    /**
     * When put in scope, additional operations can take place by the
     * {@link ObservationHandler}s such as putting entries in thread local.
     *
     * @return new scope
     */
    Scope openScope();

    interface Scope extends AutoCloseable {
        @Nullable Observation getCurrentObservation();

        @Override void close();
    }

    @SuppressWarnings("unchecked")
    class Context implements TagsProvider {
        private final Map<Class<?>, Object> map = new HashMap<>();

        private String name;

        private String displayName;

        private final Set<Tag> additionalLowCardinalityTags = new LinkedHashSet<>();

        private final Set<Tag> additionalHighCardinalityTags = new LinkedHashSet<>();

        public <T> Context put(Class<T> clazz, T object) {
            this.map.put(clazz, object);
            return this;
        }

        public String getName() {
            return this.name;
        }

        public Context setName(String name) {
            this.name = name;
            return this;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public Context setDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public void remove(Class<?> clazz) {
            this.map.remove(clazz);
        }

        @Nullable public <T> T get(Class<T> clazz) {
            return (T) this.map.get(clazz);
        }

        public <T> boolean containsKey(Class<T> clazz) {
            return this.map.containsKey(clazz);
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

        public void addLowCardinalityTag(Tag tag) {
            this.additionalLowCardinalityTags.add(tag);
        }

        public void addHighCardinalityTag(Tag tag) {
            this.additionalHighCardinalityTags.add(tag);
        }

        @Override
        public Tags getAdditionalLowCardinalityTags() {
            return Tags.of(this.additionalLowCardinalityTags);
        }

        @Override
        public Tags getAdditionalHighCardinalityTags() {
            return Tags.of(this.additionalHighCardinalityTags);
        }

        @Override
        public String toString() {
            return "Context{" +
                    "map=" + map +
                    ", name='" + name + '\'' +
                    ", displayName='" + displayName + '\'' +
                    ", lowCardinalityTags=" + getLowCardinalityTags() +
                    ", additionalLowCardinalityTags=" + additionalLowCardinalityTags +
                    ", highCardinalityTags=" + getHighCardinalityTags() +
                    ", additionalHighCardinalityTags=" + additionalHighCardinalityTags +
                    '}';
        }
    }
}
