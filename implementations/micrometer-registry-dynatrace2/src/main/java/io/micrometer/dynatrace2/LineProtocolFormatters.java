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

import io.micrometer.core.instrument.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Static formatters for line protocol
 * Formatters are not responsible of formatting metric keys, dimension keys nor dimension values as
 * this is provided by Micrometer core when implementing a corresponding
 * {@link io.micrometer.core.instrument.config.NamingConvention}
 *
 * @author Oriol Barcelona
 * @see LineProtocolNamingConvention
 */
class LineProtocolFormatters {

    private static final Logger logger = LoggerFactory.getLogger(LineProtocolFormatters.class);

    private static final DecimalFormat METRIC_VALUE_FORMAT = new DecimalFormat(
            "#.#####",
            DecimalFormatSymbols.getInstance(Locale.US));

    static String formatGaugeMetricLine(String metric, List<Tag> tags, double value, long timestamp, String entityId) {
        return String.format(
                "%s %s %d",
                formatMetricAndDimensions(metric, tags, entityId),
                formatMetricValue(value),
                timestamp);
    }

    static String formatCounterMetricLine(String metric, List<Tag> tags, double value, long timestamp, String entityId) {
        return String.format(
                "%s count,delta=%s %d",
                formatMetricAndDimensions(metric, tags, entityId),
                formatMetricValue(value),
                timestamp);
    }

    static String formatTimerMetricLine(String metric, List<Tag> tags, double value, long timestamp, String entityId) {
        return String.format(
                "%s gauge,%s %d",
                formatMetricAndDimensions(metric, tags, entityId),
                formatMetricValue(value),
                timestamp);
    }

    private static String formatMetricAndDimensions(String metric, List<Tag> tags, String entityId) {
        String[] cases = {"HOST","PROCESS_GROUP_INSTANCE","CUSTOM_DEVICE","CUSTOM_DEVICE_GROUP"};
        String entityIdString = "";
        if (!entityId.equals("")) {
            int index;
            for(index=0;index<cases.length; index++){
                if(entityId.startsWith(cases[index])) break;
            }
            switch(index){
                case 0:
                    entityIdString = "dt.entity.host="+entityId;
                    break;
                case 1:
                    entityIdString = "dt.entity.process_group_instance="+entityId;
                    break;
                case 2:
                    entityIdString = "dt.entity.custom_device="+entityId;
                    break;
                case 3:
                    entityIdString = "dt.entity.custom_device_group="+entityId;
                    break;
                default:
                    logger.debug("Entity ID Not Available");
            }
        }
        if (tags.isEmpty() ) {
            if (entityIdString.equals("")) {
                return metric;
            }
            else {
                return metric +","+entityIdString;
            }
        }
        if (entityIdString.equals("")) {
            return String.format("%s,%s", metric, formatTags(tags));
        }
        return String.format("%s,%s,%s", metric, entityIdString, formatTags(tags));
    }

    private static String formatTags(List<Tag> tags) {
        return tags.stream()
                .map(tag -> String.format("%s=\"%s\"", tag.getKey().toLowerCase(Locale.US), tag.getValue()))
                .limit(LineProtocolIngestionLimits.METRIC_LINE_MAX_DIMENSIONS)
                .collect(Collectors.joining(","));
    }

    private static String formatMetricValue(double value) {
        return METRIC_VALUE_FORMAT.format(value);
    }
}
