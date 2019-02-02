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
package io.micrometer.wavefront;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Class containing Wavefront-specific constants and related static methods.
 *
 * @author Han Zhang
 */
public class WavefrontConstants {
    /**
     * The tag key that is used to identify Wavefront-specific metric types.
     */
    public static final String WAVEFRONT_METRIC_TYPE_TAG_KEY = "wavefrontMetricType";

    /**
     * @param id                            The identifier for a metric.
     * @param wavefrontMetricTypeTagValue   The tag value that identifies a particular
     *                                      Wavefront-specific metric type.
     * @return {@code true} if the id identifies the metric type, {@code false} otherwise.
     */
    static boolean isWavefrontMetricType(Meter.Id id, String wavefrontMetricTypeTagValue) {
        Tag wavefrontMetricTypeTag = Tag.of(WAVEFRONT_METRIC_TYPE_TAG_KEY, wavefrontMetricTypeTagValue);
        for (Tag tag : id.getTags()) {
            if (tag.equals(wavefrontMetricTypeTag)) {
                return true;
            }
        }
        return false;
    }
}