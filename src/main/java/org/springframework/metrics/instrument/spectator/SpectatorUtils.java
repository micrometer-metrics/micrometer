package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import org.springframework.metrics.instrument.Measurement;
import org.springframework.metrics.instrument.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

class SpectatorUtils {
    static List<Measurement> measurements(com.netflix.spectator.api.Meter meter) {
        return stream(meter.measure().spliterator(), false)
                .map(m ->
                        new Measurement(
                                m.id().name(),
                                stream(m.id().tags().spliterator(), false)
                                        .map(t -> Tag.of(t.key(), t.value()))
                                        .toArray(Tag[]::new),
                                m.value())
                )
                .collect(Collectors.toList());
    }

    static Tag[] tags(com.netflix.spectator.api.Meter meter) {
        return stream(meter.id().tags().spliterator(), false)
                .map(t -> Tag.of(t.key(), t.value()))
                .toArray(Tag[]::new);
    }

    static Id spectatorId(Registry registry, String name, Tag... tags) {
        String[] flattenedTags = Arrays.stream(tags).flatMap(t -> Stream.of(t.getKey(), t.getValue()))
                .toArray(String[]::new);
        return registry.createId(name, flattenedTags);
    }
}
