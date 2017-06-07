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
package org.springframework.metrics.instrument;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.springframework.metrics.instrument.Tag.tags;

/**
 * @author Jon Schneider
 */
class MetersTest {
    @Test
    void customMeter() {
        NavigableSet<String> letters = new TreeSet<>();
        letters.addAll(Arrays.asList("a", "b", "c", "d", "e"));

        Meter customMeter = Meters.build("letters_after")
                .tags("letter", "b")
                .create(letters, (name, letterSet) -> {
                    SortedSet<String> after = letterSet.tailSet("b");
                    Measurement total = new Measurement(name,
                            tags("statistic", "total"),
                            letterSet.tailSet("b").size() - 1);

                    after.retainAll(Arrays.asList("a", "e", "i", "o", "u", "y"));
                    Measurement vowels = new Measurement(name,
                            tags("statistic", "vowels"),
                            after.size());

                    return Arrays.asList(total, vowels);
                });

        assertThat(customMeter.measure())
                .allSatisfy(m -> assertThat(m.getTags()).contains(Tag.of("letter", "b")))
                .anySatisfy(m -> {
                    assertThat(m.getTags()).contains(Tag.of("statistic", "total"));
                    assertThat(m.getValue()).isEqualTo(3, offset(1e-12));
                })
                .anySatisfy(m -> {
                    assertThat(m.getTags()).contains(Tag.of("statistic", "vowels"));
                    assertThat(m.getValue()).isEqualTo(1, offset(1e-12));
                });
    }
}
