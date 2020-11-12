/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.timestream2;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See https://docs.aws.amazon.com/timestream/latest/developerguide/API_Record.html
 * See https://docs.aws.amazon.com/timestream/latest/developerguide/API_Dimension.html
 * for a specification of the constraints on names and labels
 *
 * @author Guillaume Hiron
 */
public class TimestreamNamingConvention implements NamingConvention {

    //https://docs.aws.amazon.com/timestream/latest/developerguide/API_Record.html
    public static final int MAX_MEASURE_NAME_LENGTH = 256;

    //https://docs.aws.amazon.com/timestream/latest/developerguide/API_Dimension.html
    public static final int MAX_DIMENSION_NAME_LENGTH = 256;

    public static final int MAX_DIMENSION_VALUE_LENGTH = 2048;

    private static final NamingConvention nameConvention = NamingConvention.dot;

    private final Logger logger = LoggerFactory.getLogger(TimestreamNamingConvention.class);

    public TimestreamNamingConvention() {
    }

    /**
     * Names contain a base unit suffix when applicable.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String measureName = name;

        switch (type) {
            case COUNTER:
            case DISTRIBUTION_SUMMARY:
            case GAUGE:
                if (baseUnit != null && !measureName.endsWith("." + baseUnit))
                    measureName += "." + baseUnit;
                break;
        }

        switch (type) {
            case COUNTER:
                if (!measureName.endsWith(".total"))
                    measureName += ".total";
                break;
            case TIMER:
            case LONG_TASK_TIMER:
                if (baseUnit != null && !measureName.endsWith("." + baseUnit)) {
                    measureName += "." + baseUnit;
                }
                break;
        }
        return nameConvention.name(measureName, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        String tagKey = nameConvention.tagKey(key);
        if (tagKey.length() > MAX_DIMENSION_NAME_LENGTH) {
            logger.warn("Tag name '" + tagKey + "' is too long (" + tagKey.length() + ">" +
                        MAX_DIMENSION_NAME_LENGTH + ")");
        }
        return StringUtils.truncate(tagKey, MAX_DIMENSION_NAME_LENGTH);
    }

    @Override
    public String tagValue(String value) {
        if (value.length() > MAX_DIMENSION_VALUE_LENGTH) {
            logger.warn("Tag value '" + value + "' is too long (" + value.length() + ">" +
                        MAX_DIMENSION_VALUE_LENGTH + ")");
        }
        return StringUtils.truncate(value, MAX_DIMENSION_VALUE_LENGTH);
    }


}
