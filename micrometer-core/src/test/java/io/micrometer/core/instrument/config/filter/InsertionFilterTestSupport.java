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

public class InsertionFilterTestSupport {

    public static Stream<Arguments> variations() {
        return Stream.of(
                // Identifier tags : injected tags : expectation

                // Working against an empty instance
                Arguments.of(new Tag[0], new Tag[] { Tag.of("k1", "v1") }, new Tag[] { Tag.of("k1", "v1") }),
                Arguments.of(new Tag[0], new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") }),
                Arguments.of(new Tag[0], new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") }),
                // Strictly appending
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k4", "v4") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4") }),
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k5", "v5") }),
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k5", "v5"), Tag.of("k6", "v6") }),
                // Strictly prepending
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new Tag[] { Tag.of("k1", "v1") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k4", "v4"), Tag.of("k5", "v5"),
                                Tag.of("k6", "v6") }),
                Arguments.of(new Tag[] { Tag.of("k4", "v4"), Tag.of("k5", "v5"), Tag.of("k6", "v6") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k5", "v5"), Tag.of("k6", "v6") }),
                // Strictly injecting in the middle
                Arguments.of(
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k6", "v6"), Tag.of("k7", "v7") },
                        new Tag[] { Tag.of("k3", "v3") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k6", "v6"),
                                Tag.of("k7", "v7") }),
                Arguments.of(
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k6", "v6"), Tag.of("k7", "v7") },
                        new Tag[] { Tag.of("k3", "v3"), Tag.of("k4", "v4") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k6", "v6"), Tag.of("k7", "v7") }),
                Arguments.of(
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k6", "v6"), Tag.of("k7", "v7") },
                        new Tag[] { Tag.of("k3", "v3"), Tag.of("k4", "v4"), Tag.of("k5", "v5") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k5", "v5"), Tag.of("k6", "v6"), Tag.of("k7", "v7") }),
                // Interleaving injection
                Arguments.of(
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k4", "v4"), Tag.of("k6", "v6"),
                                Tag.of("k7", "v7") },
                        new Tag[] { Tag.of("k3", "v3"), Tag.of("k5", "v5") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "v3"), Tag.of("k4", "v4"),
                                Tag.of("k5", "v5"), Tag.of("k6", "v6"), Tag.of("k7", "v7") }),
                // Finally, some delicious food
                // Testing overlaps
                Arguments.of(new Tag[] { Tag.of("k1", "v1") }, new Tag[] { Tag.of("k1", "o1") },
                        new Tag[] { Tag.of("k1", "v1") }),
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") }, new Tag[] { Tag.of("k1", "o1") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") }),
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") },
                        new Tag[] { Tag.of("k3", "o3"), Tag.of("k1", "o1") },
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2"), Tag.of("k3", "o3") }),
                // Sanity check
                Arguments.of(new Tag[] {}, new Tag[] {}, new Tag[] {}),
                Arguments.of(new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") }, new Tag[] {},
                        new Tag[] { Tag.of("k1", "v1"), Tag.of("k2", "v2") }));
    }

}
