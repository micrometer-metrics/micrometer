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

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.micrometer.dynatrace2.LineProtocolIngestionLimits.DIMENSION_KEY_MAX_LENGTH;

/**
 * Static formatters for line protocol
 * Formatters are not responsible of formatting metric keys, dimension keys nor dimension values as
 * this is provided by Micrometer core when implementing a corresponding
 * {@link io.micrometer.core.instrument.config.NamingConvention}
 *
 * @author Oriol Barcelona
 * @author David Mass
 * @see LineProtocolNamingConvention
 */
class LineProtocolFormatters {

    private static final DecimalFormat METRIC_VALUE_FORMAT = new DecimalFormat(
            "#.#####",
            DecimalFormatSymbols.getInstance(Locale.US));

    static String formatGaugeMetricLine(NamingConvention namingConvention, Meter meter, Measurement measurement, long timestamp) {
        return String.format(
                "%s %s %d",
                formatMetricAndDimensions(namingConvention.name(formatMetricName(meter, measurement), meter.getId().getType()), meter.getId().getTags(), namingConvention),
                formatMetricValue(measurement.getValue()),
                timestamp);
    }

    static String formatCounterMetricLine(NamingConvention namingConvention, Meter meter, Measurement measurement, long timestamp) {
        return String.format(
                "%s count,delta=%s %d",
                formatMetricAndDimensions(namingConvention.name(formatMetricName(meter, measurement), meter.getId().getType()), meter.getId().getTags(), namingConvention),
                formatMetricValue(measurement.getValue()),
                timestamp);
    }

    static String formatTimerMetricLine(NamingConvention namingConvention, Meter meter, Measurement measurement, long timestamp) {
        return String.format(
                "%s gauge,%s %d",
                formatMetricAndDimensions(namingConvention.name(formatMetricName(meter, measurement), meter.getId().getType()), meter.getId().getTags(), namingConvention),
                formatMetricValue(measurement.getValue()),
                timestamp);
    }

    private static String formatMetricAndDimensions(String metric, List<Tag> tags, NamingConvention namingConvention) {
        if (tags.isEmpty() ) {
                return metric;
            }
        return String.format("%s,%s", metric, formatTags(tags, namingConvention));
    }

    private static String formatMetricName(Meter meter, Measurement measurement) {
        String meterName = meter.getId().getName();
        if (Streams.of(meter.measure()).count() == 1) {
            return meterName;
        }

        return String.format("%s.%s", meterName, measurement.getStatistic().getTagValueRepresentation());
    }

    private static String formatTags(List<Tag> tags, NamingConvention namingConvention) {
        return tags.stream()
                .map(tag -> String.format("%s=\"%s\"", namingConvention.tagKey(tag.getKey()), namingConvention.tagValue(tag.getValue())))
                .limit(LineProtocolIngestionLimits.METRIC_LINE_MAX_DIMENSIONS)
                .collect(Collectors.joining(","));
    }

    private static String formatMetricValue(double value) {
        return METRIC_VALUE_FORMAT.format(value);
    }

}
