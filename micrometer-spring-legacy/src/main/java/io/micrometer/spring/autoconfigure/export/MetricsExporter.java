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
package io.micrometer.spring.autoconfigure.export;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * A {@code MetricsExporter} can be used to export metrics, typically to an external
 * server running as a separate process.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 */
@FunctionalInterface
public interface MetricsExporter {

    /**
     * Returns the {@link MeterRegistry} used to register metrics with the exporter.
     *
     * @return the meter registry
     */
    MeterRegistry registry();

}
