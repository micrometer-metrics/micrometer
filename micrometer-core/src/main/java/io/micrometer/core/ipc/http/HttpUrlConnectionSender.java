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

import io.micrometer.core.instrument.util.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Map;

public class HttpUrlConnectionSender implements HttpSender {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 1000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpUrlConnectionSender(Duration connectTimeout, Duration readTimeout) {
        this.connectTimeoutMs = (int) connectTimeout.toMillis();
        this.readTimeoutMs = (int) readTimeout.toMillis();
    }

    public HttpUrlConnectionSender() {
        this.connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MS;
        this.readTimeoutMs = DEFAULT_READ_TIMEOUT_MS;
    }

    @Override
    public Response send(Request request) throws IOException {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) request.getUrl().openConnection();
            con.setConnectTimeout(connectTimeoutMs);
            con.setReadTimeout(readTimeoutMs);
            con.setRequestMethod(request.getMethod().toString());

            for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                con.setRequestProperty(header.getKey(), header.getValue());
            }

            con.setDoOutput(true);

            try (OutputStream os = con.getOutputStream()) {
                os.write(request.getEntity());
                os.flush();
            }

            int status = con.getResponseCode();

            String body = null;
            try {
                if (con.getErrorStream() != null) {
                    body = IOUtils.toString(con.getErrorStream());
                } else if (con.getInputStream() != null) {
                    body = IOUtils.toString(con.getInputStream());
                }
            } catch (IOException ignored) {
            }

            return new Response(status, body);
        } finally {
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignore) {
            }
        }
    }
}
