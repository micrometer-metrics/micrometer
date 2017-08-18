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
package io.micrometer.core.instrument;

import java.util.Collections;

public interface Gauge extends Meter {
    /**
     * Returns the current value. The act of observing the value by calling this method triggers sampling
     * of the underlying number or user-defined function that defines the value for the gauge.
     */
    double value();

    @Override
    default Iterable<Measurement> measure() {
        return Collections.singletonList(new Measurement(this::value, Statistic.Value));
    }

    @Override
    default Type getType() {
        return Type.Gauge;
    }

    interface Builder {
        Builder tags(Iterable<Tag> tags);

        default Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        Builder description(String description);

        Gauge create();
    }
}
