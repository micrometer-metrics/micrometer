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

/**
 * Relevant limits for metric ingestion
 *
 * @author Oriol Barcelona
 * @see <a href="https://www.dynatrace.com/support/help/shortlink/metric-ingestion-protocol#limits">metric ingestion limits</a>
 */
class LineProtocolIngestionLimits {
    static int METRIC_KEY_MAX_LENGTH = 250;
    static int DIMENSION_KEY_MAX_LENGTH = 100;
    static int DIMENSION_VALUE_MAX_LENGTH = 250;
    static int METRIC_LINE_MAX_DIMENSIONS = 50;
    static int METRIC_LINE_MAX_LENGTH = 2000;
    static int MAX_METRIC_LINES_PER_REQUEST = 1000;
}
