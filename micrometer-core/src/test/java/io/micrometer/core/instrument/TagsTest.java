package io.micrometer.core.instrument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Maciej Walkowiak
 */
class TagsTest {

    @Test
    void createsListWithSingleTag() {
        Iterable<Tag> tags = Tags.singletonList("k1", "v1");

        assertThat(tags).containsExactly(Tag.of("k1", "v1"));
    }
}