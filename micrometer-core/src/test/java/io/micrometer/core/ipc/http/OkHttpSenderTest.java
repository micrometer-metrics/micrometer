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

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link OkHttpSender}.
 * 
 * @author Oleksii Bondar
 */
class OkHttpSenderTest {

    @Captor
    private ArgumentCaptor<okhttp3.Request> requestCaptor;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void sendGetRequestWithoutRequestBody() throws Throwable {
        int expectedCode = 200;
        String expectedBody = "response";
        Method method = Method.GET;
        Request request = new Request(new URL("http://localhost"), new byte[0], method, new HashMap<>());

        Response actualResponse = sendRequest(request, expectedCode, expectedBody);

        assertThat(actualResponse.body()).isEqualTo(expectedBody);
        assertThat(actualResponse.code()).isEqualTo(expectedCode);
        okhttp3.Request capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.method()).isEqualTo(method.toString());
    }

    @Test
    void sendPostRequestWithEmptyRequestBody() throws Throwable {
        int expectedCode = 200;
        String expectedBody = "response";
        Method method = Method.POST;
        Request request = new Request(new URL("http://localhost"), new byte[0], method, new HashMap<>());

        Response actualResponse = sendRequest(request, expectedCode, expectedBody);

        assertThat(actualResponse.body()).isEqualTo(expectedBody);
        assertThat(actualResponse.code()).isEqualTo(expectedCode);
        okhttp3.Request capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.method()).isEqualTo(method.toString());
        assertThat(capturedRequest.body().contentType()).isEqualTo(MediaType.get("text/plain; charset=utf-8"));
    }

    @Test
    void sendRequestWithNonEmptyRequestBodyAndHeaders() throws Throwable {
        Method method = Method.POST;
        String headerName = UUID.randomUUID().toString();
        String headerValue = UUID.randomUUID().toString();
        Map<String, String> requestHeaders = Collections.singletonMap(headerName, headerValue);
        Request request = new Request(new URL("http://localhost"), new byte[10], method, requestHeaders);

        sendRequest(request, 200, "response");

        okhttp3.Request capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.method()).isEqualTo(method.toString());
        assertThat(capturedRequest.header(headerName)).isEqualTo(headerValue);
        assertThat(capturedRequest.body().contentType()).isEqualTo(MediaType.get("application/json; charset=utf-8"));
    }

    @Test
    void appendUtf8CharsetToContentType() throws Throwable {
        String headerName = "Content-Type";
        String contentType = "application/xml";
        Map<String, String> requestHeaders = Collections.singletonMap(headerName, contentType);
        Request request = new Request(new URL("http://localhost"), new byte[10], Method.POST, requestHeaders);

        sendRequest(request, 200, "response");

        okhttp3.Request capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.header(headerName)).isEqualTo(contentType);
        assertThat(capturedRequest.body().contentType()).isEqualTo(MediaType.get(contentType + "; charset=utf-8"));
    }

    private Response sendRequest(Request request, int expectedCode, String expectedBody) throws Throwable {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        OkHttpSender sender = new OkHttpSender(httpClient);
        Call call = mock(Call.class);
        when(httpClient.newCall(requestCaptor.capture())).thenReturn(call);

        ResponseBody responseBody = ResponseBody.create(MediaType.get("application/json"), expectedBody);
        String url = "http://localhost";
        okhttp3.Request okRequest = new okhttp3.Request.Builder().url(url).build();
        okhttp3.Response okResponse = new okhttp3.Response.Builder().request(okRequest).protocol(Protocol.HTTP_1_1)
                .message("message").code(expectedCode).body(responseBody).build();
        when(call.execute()).thenReturn(okResponse);

        return sender.send(request);
    }
}
