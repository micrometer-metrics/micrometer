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
package io.micrometer.core.instrument;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationDocumentation;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.observation.docs.ObservationDocumentation;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URI;

class OkHttpClientTimingInstrumentationVerificationTests
        extends HttpClientTimingInstrumentationVerificationTests<OkHttpClient> {

    @Override
    protected void sendHttpRequest(OkHttpClient instrumentedClient, HttpMethod method, @Nullable byte[] body,
            URI baseUri, String templatedPath, String... pathVariables) {
        Request request = new Request.Builder().method(method.name(), body == null ? null : RequestBody.create(body))
            .url(baseUri + substitutePathVariables(templatedPath, pathVariables))
            .header(OkHttpMetricsEventListener.URI_PATTERN, templatedPath)
            .build();
        try (Response ignored = instrumentedClient.newCall(request).execute()) {
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected OkHttpClient clientInstrumentedWithMetrics() {
        return new OkHttpClient.Builder()
            .eventListener(OkHttpMetricsEventListener.builder(getRegistry(), timerName()).build())
            .build();
    }

    @Override
    protected OkHttpClient clientInstrumentedWithObservations() {
        return new OkHttpClient.Builder()
            .addInterceptor(OkHttpObservationInterceptor.builder(getObservationRegistry(), timerName()).build())
            .build();
    }

    @Override
    protected ObservationDocumentation observationDocumentation() {
        return OkHttpObservationDocumentation.DEFAULT;
    }

}
