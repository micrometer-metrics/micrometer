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
package io.micrometer.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;

/**
 * Base meter visitor. By default it does nothing if the method for the type that's being visited was not overrided.
 */
public interface MeterVisitor {

    default void defaultVisit(Meter meter) {
    }

    default void visitTimer(Timer timer) {
        defaultVisit(timer);
    }

    default void visitCounter(Counter counter) {
        defaultVisit(counter);
    }

    default void visitGauge(Gauge gauge) {
        defaultVisit(gauge);
    }

    default void visitLongTaskTimer(LongTaskTimer longTaskTimer) {
        defaultVisit(longTaskTimer);
    }

    default void visitTimeGauge(TimeGauge timeGauge) {
        visitGauge(timeGauge);
    }

    default void visitDistributionSummary(DistributionSummary distributionSummary) {
        defaultVisit(distributionSummary);
    }

    default void visitFunctionTimer(FunctionCounter functionCounter) {
        defaultVisit(functionCounter);
    }

    default void visitFunctionCounter(FunctionTimer functionTimer) {
        defaultVisit(functionTimer);
    }
}
