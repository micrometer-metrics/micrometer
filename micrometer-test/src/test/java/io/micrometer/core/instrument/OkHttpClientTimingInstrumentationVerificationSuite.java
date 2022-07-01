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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

class OkHttpClientTimingInstrumentationVerificationSuite extends HttpClientTimingInstrumentationVerificationSuite {

    OkHttpClient httpClient = new OkHttpClient.Builder()
            .eventListener(OkHttpMetricsEventListener.builder(getRegistry(), timerName()).build()).build();

    @Override
    void sendGetRequest(WireMockRuntimeInfo wmRuntimeInfo, String path) {
        Request request = new Request.Builder().url(wmRuntimeInfo.getHttpBaseUrl() + "/" + path).build();
        try (Response response = httpClient.newCall(request).execute()) {
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
