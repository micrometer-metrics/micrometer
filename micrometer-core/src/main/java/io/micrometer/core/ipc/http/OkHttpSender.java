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

/**
 * OkHttp-based {@link HttpSender}.
 *
 * @author Jon Schneider
 * @since 1.1.0
 */
public class OkHttpSender implements HttpSender {

    private static final MediaType MEDIA_TYPE_APPLICATION_JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType MEDIA_TYPE_TEXT_PLAIN = MediaType.get("text/plain; charset=utf-8");

    private final OkHttpClient client;

    public OkHttpSender(OkHttpClient client) {
        this.client = client;
    }

    public OkHttpSender() {
        this(new OkHttpClient());
    }

    @Override
    public Response send(Request request) throws Throwable {
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder().url(request.getUrl());

        for (Map.Entry<String, String> requestHeader : request.getRequestHeaders().entrySet()) {
            requestBuilder.addHeader(requestHeader.getKey(), requestHeader.getValue());
        }

        byte[] entity = request.getEntity();
        String requestMethod = request.getMethod().toString();
        if (entity.length > 0) {
            String contentType = request.getRequestHeaders().get("Content-Type");
            MediaType mediaType = contentType != null
                    ? MediaType.get(contentType + "; charset=utf-8")
                    : MEDIA_TYPE_APPLICATION_JSON;
            RequestBody body = RequestBody.create(mediaType, entity);
            requestBuilder.method(requestMethod, body);
        } else {
            if (okhttp3.internal.http.HttpMethod.requiresRequestBody(requestMethod)) {
                RequestBody body = RequestBody.create(MEDIA_TYPE_TEXT_PLAIN, entity);
                requestBuilder.method(requestMethod, body);
            } else {
                requestBuilder.method(requestMethod, null);
            }
        }

        okhttp3.Response response = client.newCall(requestBuilder.build()).execute();
        return new Response(response.code(), response.body() == null ? null : response.body().string());
    }
}
