/*
 * Copyright 2022 VMware, Inc.
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
package io.micrometer.observation.transport.http.tags;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.http.HttpRequest;
import io.micrometer.observation.transport.http.HttpResponse;
import io.micrometer.observation.transport.http.context.HttpContext;

/**
 * Default implementation of {@link HttpKeyValueProvider} that can be extended for
 * customization.
 *
 * @author Tommy Ludwig
 * @since 1.10.0
 */
public class DefaultHttpKeyValuesProvider implements Observation.GlobalKeyValuesProvider<HttpContext> {

    @Override
    public KeyValues getLowCardinalityKeyValues(HttpContext context) {
        HttpRequest request = context.getRequest();
        HttpResponse response = context.getResponse();
        Throwable exception = context.getError().orElse(null);
        return KeyValues.of(HttpKeyValues.method(request), HttpKeyValues.uri(request), HttpKeyValues.status(response),
                HttpKeyValues.outcome(response), HttpKeyValues.exception(exception));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(HttpContext context) {
        return KeyValues.empty();
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof HttpContext;
    }
}
