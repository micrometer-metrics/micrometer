/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.ipc.http;

import okhttp3.*;

import java.util.Map;

public class OkHttpHttpClient implements HttpClient {
    private final OkHttpClient client;

    public OkHttpHttpClient(OkHttpClient client) {
        this.client = client;
    }

    public OkHttpHttpClient() {
        this(new OkHttpClient());
    }

    @Override
    public HttpResponse send(HttpRequest request) throws Throwable {
        Request.Builder requestBuilder = new Request.Builder().url(request.getUrl());

        for (Map.Entry<String, String> requestHeader : request.getRequestHeaders().entrySet()) {
            requestBuilder.addHeader(requestHeader.getKey(), requestHeader.getValue());
        }

        if (request.getEntity().length > 0) {
            String contentType = request.getRequestHeaders().get("Content-Type");
            if (contentType == null)
                contentType = "application/json"; // guess
            RequestBody body = RequestBody.create(MediaType.get(contentType + "; charset=utf-8"), request.getEntity());
            requestBuilder.method(request.getMethod().toString(), body);
        } else {
            if (okhttp3.internal.http.HttpMethod.requiresRequestBody(request.getMethod().toString())) {
                RequestBody body = RequestBody.create(MediaType.get("text/plain; charset=utf-8"), request.getEntity());
                requestBuilder.method(request.getMethod().toString(), body);
            } else {
                requestBuilder.method(request.getMethod().toString(), null);
            }
        }

        Response response = client.newCall(requestBuilder.build()).execute();
        return new HttpResponse(response.code(), response.body() == null ? null : response.body().string());
    }
}
