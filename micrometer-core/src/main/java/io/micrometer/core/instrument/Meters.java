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

import io.micrometer.core.instrument.lazy.LazyCounter;
import io.micrometer.core.instrument.lazy.LazyDistributionSummary;
import io.micrometer.core.instrument.lazy.LazyLongTaskTimer;
import io.micrometer.core.instrument.lazy.LazyTimer;

import java.util.function.Supplier;

/**
 * @author Jon Schneider
 */
public class Meters {
    public static Counter lazyCounter(Supplier<Counter> counterBuilder) {
        return new LazyCounter(counterBuilder);
    }

    public static Timer lazyTimer(Supplier<Timer> timerBuilder) {
        return new LazyTimer(timerBuilder);
    }

    public static DistributionSummary lazySummary(Supplier<DistributionSummary> summaryBuilder) {
        return new LazyDistributionSummary(summaryBuilder);
    }

    public static LongTaskTimer lazyLongTaskTimer(Supplier<LongTaskTimer> timerBuilder) {
        return new LazyLongTaskTimer(timerBuilder);
    }
}
