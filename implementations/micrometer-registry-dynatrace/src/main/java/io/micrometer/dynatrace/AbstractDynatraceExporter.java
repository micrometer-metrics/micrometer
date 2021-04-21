/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.ipc.http.HttpSender;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * Base class for implementations of Dynatrace exporters.
 *
 * @author Georg Pirklbauer
 * @since 1.8.0
 */
public abstract class AbstractDynatraceExporter {

    protected DynatraceConfig config;
    protected Clock clock;
    protected HttpSender httpClient;

    public AbstractDynatraceExporter(DynatraceConfig config, Clock clock, HttpSender httpClient) {
        this.config = config;
        this.clock = clock;
        this.httpClient = httpClient;
    }

    public TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public abstract void export(@Nonnull MeterRegistry registry);
}
