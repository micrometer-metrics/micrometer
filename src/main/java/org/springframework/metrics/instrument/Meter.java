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

/**
 * A counter, gauge, timer, or distribution summary that results collects one or more metrics.
 */
public interface Meter {
    String getName();

    Iterable<Tag> getTags();

    Type getType();

    /**
     * Get the set of measurements for this meter.
     */
    Iterable<Measurement> measure();

    /**
     * Custom meters may emit metrics like one of these types without implementing
     * the corresponding interface. For example, a heisen-counter like structure
     * will emit the same metric as a {@link Counter} but does not have the same
     * increment-driven API.
     */
    enum Type {
        Counter,
        Gauge,
        LongTaskTimer,
        Timer,
        DistributionSummary,
        Other
    }
}
