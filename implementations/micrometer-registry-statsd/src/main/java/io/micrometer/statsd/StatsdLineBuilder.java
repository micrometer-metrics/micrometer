/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Statistic;

/**
 * A StatsD format serializer for an individual {@link Meter}. There is an instance per
 * meter so that name normalization can be cached early and kept for subsequent writes
 * without incurring a lookup cost.
 *
 * @author Jon Schneider
 */
public interface StatsdLineBuilder {

    default String count(long amount) {
        return count(amount, Statistic.COUNT);
    }

    String count(long amount, Statistic stat);

    default String gauge(double amount) {
        return gauge(amount, Statistic.VALUE);
    }

    String gauge(double amount, Statistic stat);

    String histogram(double amount);

    String timing(double timeMs);

}
