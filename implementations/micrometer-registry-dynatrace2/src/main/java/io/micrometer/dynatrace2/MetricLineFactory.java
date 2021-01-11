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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;

import java.util.stream.Stream;

/**
 * Metric line factory which maps from micrometer domain to expected format in line protocol
 *
 * @author Oriol Barcelona
 * @author David Mass
 */
class MetricLineFactory {
    private final Clock clock;
    private final NamingConvention namingConvention;

    MetricLineFactory(Clock clock, NamingConvention lineProtocolNamingConvention) {
        this.clock = clock;
        this.namingConvention = lineProtocolNamingConvention;
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
                this::toTimerLine,
                this::toDistributionSummaryLine,
                this::toLongTaskTimerLine,
                this::toTimeGaugeLine,
                this::toFunctionCounterLine,
                this::toFunctionTimerLine,
                this::toMeterLine
        );
    }

    private Stream<String> toGaugeLine(Gauge meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatGaugeMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toCounterLine(Counter meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatCounterMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toTimerLine(Timer meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatTimerMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toDistributionSummaryLine(DistributionSummary meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatGaugeMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toLongTaskTimerLine(LongTaskTimer meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatTimerMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toTimeGaugeLine(TimeGauge meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatGaugeMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toFunctionCounterLine(FunctionCounter meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatCounterMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toFunctionTimerLine(FunctionTimer meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatTimerMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }

    private Stream<String> toMeterLine(Meter meter) {
        long wallTime = clock.wallTime();

        return Streams.of(meter.measure())
                .map(measurement -> LineProtocolFormatters.formatGaugeMetricLine(
                        namingConvention,
                        meter,
                        measurement,
                        wallTime));
    }
}
