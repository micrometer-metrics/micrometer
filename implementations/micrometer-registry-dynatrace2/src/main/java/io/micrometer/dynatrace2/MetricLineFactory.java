/**
 * Copyright 2020 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.dynatrace2;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;

import java.util.stream.Stream;

/**
 * Metric line factory which maps from micrometer domain to expected format in line protocol
 *
 * @author Oriol Barcelona
 */
class MetricLineFactory {
    private final Clock clock;

    MetricLineFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Creates the formatted metric lines for the corresponding meter. A meter will have multiple
     * metric lines considering the measurements within.
     *
     * @param meter to extract the measurements
     * @return a stream of formatted metric lines
     */
    Stream<String> toMetricLines(Meter meter) {
        return meter.match(
                this::toGaugeLine,
                this::toCounterLine,
                this::toEmpty,
                this::toEmpty,
                this::toEmpty,
                this::toEmpty,
                this::toEmpty,
                this::toEmpty,
                this::toEmpty
        );
    }

    private Stream<String> toGaugeLine(Gauge meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatGaugeMetricLine(
                        metricName(meter, measurement),
                        meter.getId().getTags(),
                        measurement.getValue(),
                        wallTime));
    }

    private Stream<String> toCounterLine(Counter meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatCounterMetricLine(
                        metricName(meter, measurement),
                        meter.getId().getTags(),
                        measurement.getValue(),
                        wallTime));
    }

    private Stream<String> toEmpty(Meter meter) {
        return Stream.empty();
    }

    private String metricName(Meter meter, Measurement measurement) {
        String meterName = meter.getId().getName();
        if (Streams.of(meter.measure()).count() == 1) {
            return meterName;
        }

        return String.format("%s.%s", meterName, measurement.getStatistic().getTagValueRepresentation());
    }
}
