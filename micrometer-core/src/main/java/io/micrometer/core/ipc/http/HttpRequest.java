/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.core.ipc.http;

import io.micrometer.core.instrument.util.JsonUtils;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

public class HttpRequest {
    private final byte[] entity;
    private final HttpMethod method;
    private final Map<String, String> requestHeaders;

    public HttpRequest(byte[] entity, HttpMethod method, Map<String, String> requestHeaders) {
        this.entity = entity;
        this.method = method;
        this.requestHeaders = requestHeaders;
    }

    public byte[] getEntity() {
        return entity;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public static HttpRequest.Builder build(String uri, HttpClient sender) {
        return new HttpRequest.Builder(uri, sender);
    }

    @Override
    public String toString() {
        if (entity.length == 0) {
            return "<no request body>";
        } else if ("application/json".equals(requestHeaders.get("Content-Type"))) {
            return JsonUtils.prettyPrint(new String(entity));
        }
        return new String(entity);
    }

    public static class Builder {
        private static final String APPLICATION_JSON = "application/json";
        private static final String TEXT_PLAIN = "text/plain";

        private final URL url;
        private final HttpClient sender;

        private byte[] entity = new byte[0];
        private HttpMethod method;
        private Map<String, String> requestHeaders = new LinkedHashMap<>();

        Builder(String uri, HttpClient sender) {
            try {
                this.url = URI.create(uri).toURL();
            } catch (MalformedURLException ex) {
                throw new UncheckedIOException(ex);
            }
            this.sender = sender;
        }

        /**
         * Add a header to the request.
         *
         * @return This request builder.
         */
        public final Builder withHeader(String name, String value) {
            requestHeaders.put(name, value);
            return this;
        }

        /**
         * If user and password are non-empty, set basic authentication on the request.
         *
         * @param user     A user name, if available.
         * @param password A password, if available.
         * @return This request builder.
         */
        public final Builder withBasicAuthentication(@Nullable String user, @Nullable String password) {
            if (user != null && StringUtils.isNotBlank(user)) {
                String encoded = Base64.getEncoder().encodeToString((user.trim() + ":" + (password == null ? "" : password.trim()))
                        .getBytes(StandardCharsets.UTF_8));
                withHeader("Authorization", "Basic " + encoded);
            }
            return this;
        }

        /**
         * Set the request body as JSON.
         *
         * @return This request builder.
         */
        public final Builder withJsonContent(String content) {
            return withContent(APPLICATION_JSON, content);
        }

        /**
         * Set the request body as JSON.
         *
         * @return This request builder.
         */
        public final Builder withPlainText(String content) {
            return withContent(TEXT_PLAIN, content);
        }

        /**
         * Set the request body.
         *
         * @return This request builder.
         */
        public final Builder withContent(String type, String content) {
            return withContent(type, content.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Set the request body.
         *
         * @return This request builder.
         */
        public final Builder withContent(String type, byte[] content) {
            withHeader("Content-Type", type);
            entity = content;
            return this;
        }

        /**
         * Add header to accept {@code application/json} data.
         *
         * @return This request builder;
         */
        public Builder acceptJson() {
            return accept(APPLICATION_JSON);
        }

        /**
         * Add accept header.
         *
         * @return This request builder.
         */
        public Builder accept(String type) {
            return withHeader("Accept", type);
        }

        /**
         * Set the request method.
         *
         * @return This request builder.
         */
        public final Builder withMethod(HttpMethod method) {
            this.method = method;
            return this;
        }

        public final Builder compress() throws IOException {
            this.entity = gzip(entity);
            return this;
        }

        public final Builder compressWhen(Supplier<Boolean> when) throws IOException {
            if (when.get())
                return compress();
            return this;
        }

        private static byte[] gzip(byte[] data) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream out = new GZIPOutputStream(bos)) {
                out.write(data);
            }
            return bos.toByteArray();
        }

        public final Builder print() {
            System.out.println(new HttpRequest(entity, method, requestHeaders));
            return this;
        }

        public HttpResponse send() throws Throwable {
            return sender.send(url, new HttpRequest(entity, method, requestHeaders));
        }
    }
}
