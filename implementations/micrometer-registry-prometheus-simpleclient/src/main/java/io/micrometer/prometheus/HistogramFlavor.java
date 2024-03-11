/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.prometheus;

/**
 * Histogram flavors.
 *
 * @deprecated since 1.13.0, unfortunately there is no replacement right now since the new
 * Prometheus client does not support custom histogram bucket names.
 * @author Jon Schneider
 * @since 1.4.0
 */
@Deprecated
public enum HistogramFlavor {

    Prometheus, VictoriaMetrics

}
