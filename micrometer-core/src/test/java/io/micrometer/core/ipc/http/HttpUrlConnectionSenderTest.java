/**
 * Copyright 2019 Pivotal Software, Inc.
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

import io.micrometer.core.ipc.http.HttpSender.Method;
import io.micrometer.core.ipc.http.HttpSender.Request;
import io.micrometer.core.ipc.http.HttpSender.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HttpUrlConnectionSender}.
 * 
 * @author Oleksii Bondar
 */
class HttpUrlConnectionSenderTest {

    private static HttpUrlStreamHandler httpUrlStreamHandler;

    @BeforeAll
    public static void setupURLStreamHandlerFactory() {
        // Allows for mocking URL connections
        URLStreamHandlerFactory urlStreamHandlerFactory = mock(URLStreamHandlerFactory.class);
        URL.setURLStreamHandlerFactory(urlStreamHandlerFactory);

        httpUrlStreamHandler = new HttpUrlStreamHandler();
        when(urlStreamHandlerFactory.createURLStreamHandler("http")).thenReturn(httpUrlStreamHandler);
    }

    @BeforeEach
    void setup() {
        httpUrlStreamHandler.resetConnections();
    }

    @Test
    void sendRequestWithDefaultTimeoutSettings() throws IOException {
        HttpUrlConnectionSender sender = new HttpUrlConnectionSender();
        URL url = new URL("http://localhost");
        Request request = createRequest(url);
        String headerName = UUID.randomUUID().toString();
        String headerValue = UUID.randomUUID().toString();
        Map<String, String> requestHeaders = Collections.singletonMap(headerName, headerValue);
        when(request.getRequestHeaders()).thenReturn(requestHeaders);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        int expectedResponseCode = 200;
        String expectedBody = "responseBody";
        byte[] bytes = expectedBody.getBytes();
        try (OutputStream bos = new ByteArrayOutputStream(); InputStream is = new ByteArrayInputStream(bytes)) {
            when(connection.getOutputStream()).thenReturn(bos);
            when(connection.getResponseCode()).thenReturn(expectedResponseCode);
            when(connection.getInputStream()).thenReturn(is);
            httpUrlStreamHandler.addConnection(url, connection);

            Response response = sender.send(request);

            assertThat(response.code()).isEqualTo(expectedResponseCode);
            assertThat(response.body()).isEqualTo(expectedBody);
            verify(connection).setReadTimeout(eq(10000));
            verify(connection).setConnectTimeout(eq(1000));
            verify(connection).setDoOutput(true);
            verify(connection).setRequestMethod(eq(request.getMethod().toString()));
            verify(connection).getErrorStream();
            verify(connection).getOutputStream();
            verify(connection, times(2)).getInputStream();
            verify(connection).disconnect();
        }
    }

    @Test
    void receiveErrorResponse() throws IOException {
        HttpUrlConnectionSender sender = new HttpUrlConnectionSender();
        URL url = new URL("http://localhost");
        Request request = createRequest(url);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        int expectedResponseCode = 200;
        String expectedBody = "responseBody";
        byte[] bytes = expectedBody.getBytes();
        try (OutputStream bos = new ByteArrayOutputStream(); InputStream is = new ByteArrayInputStream(bytes)) {
            when(connection.getOutputStream()).thenReturn(bos);
            when(connection.getResponseCode()).thenReturn(expectedResponseCode);
            when(connection.getErrorStream()).thenReturn(is);
            httpUrlStreamHandler.addConnection(url, connection);

            Response response = sender.send(request);

            assertThat(response.code()).isEqualTo(expectedResponseCode);
            assertThat(response.body()).isEqualTo(expectedBody);
            verify(connection, times(2)).getErrorStream();
        }
    }

    @Test
    void allowToOverrideConnectionSettings() throws IOException {
        Duration readTimeout = Duration.ofMillis(100);
        Duration connectTimeout = Duration.ofMillis(200);
        HttpUrlConnectionSender sender = new HttpUrlConnectionSender(connectTimeout, readTimeout);
        URL url = new URL("http://localhost");
        Request request = createRequest(url);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        byte[] bytes = "responseBody".getBytes();
        try (OutputStream bos = new ByteArrayOutputStream(); InputStream is = new ByteArrayInputStream(bytes)) {
            when(connection.getOutputStream()).thenReturn(bos);
            when(connection.getResponseCode()).thenReturn(200);
            when(connection.getErrorStream()).thenReturn(is);
            httpUrlStreamHandler.addConnection(url, connection);

            sender.send(request);

            verify(connection).setReadTimeout(eq((int) readTimeout.toMillis()));
            verify(connection).setConnectTimeout(eq((int) connectTimeout.toMillis()));
        }
    }

    private Request createRequest(URL url) {
        Request request = mock(Request.class);
        when(request.getUrl()).thenReturn(url);
        when(request.getMethod()).thenReturn(Method.GET);
        when(request.getEntity()).thenReturn(new byte[1]);
        when(request.getRequestHeaders()).thenReturn(new HashMap<>());
        return request;
    }

    // needed since can't mock final URL class
    private static class HttpUrlStreamHandler extends URLStreamHandler {

        private Map<URL, URLConnection> connections = new HashMap<>();

        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return connections.get(url);
        }

        public void resetConnections() {
            connections = new HashMap<>();
        }

        public HttpUrlStreamHandler addConnection(URL url, URLConnection urlConnection) {
            connections.put(url, urlConnection);
            return this;
        }
    }
}
