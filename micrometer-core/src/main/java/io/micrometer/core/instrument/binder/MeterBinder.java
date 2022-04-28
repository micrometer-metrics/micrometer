/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder;

import io.micrometer.common.lang.NonNull;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Binders register one or more metrics to provide information about the state of some
 * aspect of the application or its container.
 * <p>
 * Binders are enabled by default if they source data for an alert that is recommended for
 * a production ready app.
 */
public interface MeterBinder {

    void bindTo(@NonNull MeterRegistry registry);

}
