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
package io.micrometer.core.instrument.binder.okhttp3;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.common.lang.NonNullFields;
import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.transport.http.tags.HttpClientKeyValuesConvention;
import okhttp3.Request;
import okhttp3.Response;

import java.util.function.BiFunction;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

@NonNullApi
@NonNullFields
public class StandardizedOkHttpKeyValuesProvider implements OkHttpKeyValuesProvider {

    private final HttpClientKeyValuesConvention keyValuesConvention;

    public StandardizedOkHttpKeyValuesProvider(HttpClientKeyValuesConvention keyValuesConvention) {
        this.keyValuesConvention = keyValuesConvention;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(OkHttpContext context) {
        OkHttpMetricsEventListener.CallState state = context.getState();
        Request request = state.request;
        Iterable<BiFunction<Request, Response, Tag>> contextSpecificTags = context.getContextSpecificTags();
        // TODO: What to do when there is no request or response - do we set UNKNOWN ?
        return KeyValues.of(keyValuesConvention.all(context.getRequest(), context.getResponse()))
                .and(stream(contextSpecificTags.spliterator(), false)
                        .map(contextTag -> contextTag.apply(request, state.response))
                        .map(tag -> KeyValue.of(tag.getKey(), tag.getValue()))
                        .collect(toList()));
    }

}
