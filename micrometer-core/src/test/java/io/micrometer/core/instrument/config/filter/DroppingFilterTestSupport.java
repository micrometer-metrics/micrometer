/*
 * Copyright 2025 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.config.filter;

import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

public class DroppingFilterTestSupport {

    public static Stream<Arguments> variations() {
        return Stream.of(
                // Identifier tags : forbidden keys : expected result
                // Using 0..3 samples to be sure that all implementations are invoked
                // Boring no-op
                Arguments.of(new Tag[] {}, new String[] {}, new Tag[] {}),
                Arguments.of(new Tag[] {}, new String[] { "k1" }, new Tag[] {}),
                Arguments.of(new Tag[] {}, new String[] { "k1", "k2" }, new Tag[] {}),
                Arguments.of(new Tag[] {}, new String[] { "k1", "k2", "k3" }, new Tag[] {}),
                Arguments.of(new Tag[] { Tag.of("k4", "v4") }, new String[] { "k1", "k2", "k3" },
                        new Tag[] { Tag.of("k4", "v4") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4") }, new String[] {}, new Tag[] { Tag.of("k4", "v4") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5") }, new String[] { "k1", "k2", "k3" },
                        new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k1", "k2", "k3" },
                        new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") }),
                // Finally, let's start actually dropping stuff
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k5" }, new Tag[] { Tag.of("k4", "v4"), Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k1", "k5" }, new Tag[] { Tag.of("k4", "v4"), Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k1", "k5", "k3" }, new Tag[] { Tag.of("k4", "v4"), Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k4", "k5" }, new Tag[] { Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new String[] { "k4", "k5", "k6" }, new Tag[] {}),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5") }, new String[] { "k4", "k5" },
                        new Tag[] {}),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5") }, new String[] { "k4" },
                        new Tag[] { Tag.of("k5", "v5") }));
    }

}
