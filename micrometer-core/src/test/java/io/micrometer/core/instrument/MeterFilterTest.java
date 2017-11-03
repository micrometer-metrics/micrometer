package io.micrometer.core.instrument;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;

import static java.util.stream.StreamSupport.stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class MeterFilterTest {
    @Test
    void commonTags() {
        MeterFilter filter = MeterFilter.commonTags(Tags.zip("k2", "v2"));
        Meter.Id id = new Meter.Id("name", Tags.zip("k1", "v1"), null, null);

        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("k1", "v1"));
        assertThat(filteredId).has(tag("k2", "v2"));
    }

    @Test
    void ignoreTags() {
        MeterFilter filter = MeterFilter.ignoreTags("k1", "k2");
        Meter.Id id = new Meter.Id("name", Tags.zip("k1", "v1", "k2", "v2", "k3", "v3"), null, null);

        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("k3"));
        assertThat(filteredId).doesNotHave(tag("k1"));
        assertThat(filteredId).doesNotHave(tag("k2"));
    }

    @Test
    void replaceTagValues() {
        MeterFilter filter = MeterFilter.replaceTagValues("status", s -> s.charAt(0) + "xx", "200");

        Meter.Id id = new Meter.Id("name", Tags.zip("status", "400"), null, null);
        Meter.Id filteredId = filter.map(id);
        assertThat(filteredId).has(tag("status", "4xx"));

        id = new Meter.Id("name", Tags.zip("status", "200"), null, null);
        filteredId = filter.map(id);
        assertThat(filteredId).has(tag("status", "200"));
    }

    private static Condition<Meter.Id> tag(String tagKey) {
        return tag(tagKey, null);
    }

    private static Condition<Meter.Id> tag(String tagKey, String tagValue) {
        return new Condition<>(
            id -> stream(id.getTags().spliterator(), false)
                .anyMatch(t -> t.getKey().equals(tagKey) && (tagValue == null || t.getValue().equals(tagValue))),
            "Must have a tag with key '" + tagKey + "'");
    }
}
