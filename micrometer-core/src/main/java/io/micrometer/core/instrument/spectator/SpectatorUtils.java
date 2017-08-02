/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Tag;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

public class SpectatorUtils {
    public static List<Measurement> measurements(com.netflix.spectator.api.Meter meter) {
        return stream(meter.measure().spliterator(), false)
                .map(m ->
                        new Measurement(
                                m.id().name(),
                                stream(m.id().tags().spliterator(), false)
                                        .map(t -> Tag.of(t.key(), t.value()))
                                        .collect(Collectors.toList()),
                                m.value())
                )
                .collect(Collectors.toList());
    }

    public static List<Tag> tags(com.netflix.spectator.api.Meter meter) {
        return stream(meter.id().tags().spliterator(), false)
                .map(t -> Tag.of(t.key(), t.value()))
                .collect(Collectors.toList());
    }

    public static Id spectatorId(Registry registry, String name, Iterable<Tag> tags) {
        String[] flattenedTags = stream(tags.spliterator(), false)
                .flatMap(t -> Stream.of(t.getKey(), t.getValue()))
                .toArray(String[]::new);
        return registry.createId(name, flattenedTags);
    }

    public static com.netflix.spectator.api.Clock spectatorClock(Clock clock) {
        return new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return clock.wallTime();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        };
    }
}
