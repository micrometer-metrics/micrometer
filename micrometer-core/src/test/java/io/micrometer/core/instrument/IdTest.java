package io.micrometer.core.instrument;

import io.micrometer.core.instrument.internal.MeterId;
import org.junit.jupiter.api.Test;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

class IdTest {
    @Test
    void withStatistic() {
        MeterId id = new MeterId("my.id", emptyList(), null, null);
        assertThat(id.withTag(Statistic.TotalTime).getTags()).contains(Tag.of("statistic", "totalTime"));
    }
}