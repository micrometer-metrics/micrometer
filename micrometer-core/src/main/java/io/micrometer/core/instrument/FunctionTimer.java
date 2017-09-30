/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public interface FunctionTimer extends Meter {
    /**
     * The total number of occurrences of the timed event.
     */
    long count();

    /**
     * The total time of all occurrences of the timed event.
     */
    double totalTime(TimeUnit unit);

    TimeUnit baseTimeUnit();

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
            new Measurement(() -> (double) count(), Statistic.Count),
            new Measurement(() -> totalTime(baseTimeUnit()), Statistic.Total)
        );
    }
}
