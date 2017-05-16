package org.springframework.metrics.instrument.internal;

import org.junit.jupiter.api.Test;
import org.springframework.metrics.instrument.Tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.metrics.instrument.internal.MeterId.id;

class MeterIdTest {

    @Test
    void idEqualityAndHashCode() {
        MeterId id1 = id("foo", Tags.tagList("k1", "v1", "k2", "v2"));
        MeterId id2 = id("foo", Tags.tagList("k1", "v1", "k2", "v2"));
        MeterId id3 = id("foo", Tags.tagList("k2", "v2", "k1", "v1"));
        MeterId id4 = id("foo", Tags.tagList("k1", "v1"));
        MeterId id5 = id("bar", Tags.tagList("k1", "v1", "k2", "v2"));

        assertThat(id1)
                .isEqualTo(id2)
                .isEqualTo(id3)
                .isNotEqualTo(id4)
                .isNotEqualTo(id5);

        assertThat(id1.hashCode())
                .isEqualTo(id2.hashCode())
                .isEqualTo(id3.hashCode())
                .isNotEqualTo(id4.hashCode())
                .isNotEqualTo(id5.hashCode());
    }
}
