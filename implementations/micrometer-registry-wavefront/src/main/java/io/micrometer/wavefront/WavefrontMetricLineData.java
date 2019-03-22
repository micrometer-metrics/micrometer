/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.wavefront;

/**
 * Data for a single metric or distribution that is formatted as Wavefront line data.
 *
 * @author Han Zhang
 * @since 1.2.0
 */
class WavefrontMetricLineData {
    private final String lineData;
    private final boolean isDistribution;

    WavefrontMetricLineData(String lineData, boolean isDistribution) {
        this.lineData = lineData;
        this.isDistribution = isDistribution;
    }

    String lineData() {
        return this.lineData;
    }

    boolean isDistribution() {
        return this.isDistribution;
    }
}
