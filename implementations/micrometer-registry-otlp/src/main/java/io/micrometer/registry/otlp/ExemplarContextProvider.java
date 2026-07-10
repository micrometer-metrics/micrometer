/*
 * Copyright 2025 VMware, Inc.
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
package io.micrometer.registry.otlp;

import org.jspecify.annotations.Nullable;

/**
 * Provides exemplar information that can be attached to metrics.
 *
 * @author Jonatan Ivanov
 * @since 1.17.0
 */
public interface ExemplarContextProvider {

    /**
     * The exemplar information returned by this method must contain the necessary
     * information to create an exemplar. If the exemplar information is not available
     * (there is no current span or the span is not sampled) or the implementor does not
     * want this particular span to be used as an exemplar, the method should return
     * {@code null}.
     * @return context object if available, null otherwise
     */
    @Nullable OtlpExemplarContext getExemplarContext();

}
