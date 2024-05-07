/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.core.instrument.binder.http;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link HttpJakartaServletRequestTags} and {@link HttpRequestTags}.
 *
 * @author Brian Clozel
 */
class HttpJakartaServletRequestTagsTest {

    @Test
    void nullRequestShouldContributeUnknownMethodTag() {
        Tag unknownMethod = Tag.of("method", "UNKNOWN");
        HttpServletRequest jakartaRequest = null;
        Tag result = HttpJakartaServletRequestTags.method(jakartaRequest);
        assertThat(result).isEqualTo(unknownMethod);

        javax.servlet.http.HttpServletRequest javaxRequest = null;
        result = HttpRequestTags.method(javaxRequest);
        assertThat(result).isEqualTo(unknownMethod);
    }

    @Test
    void requestShouldContributeMethodTag() {
        Tag httpGet = Tag.of("method", "GET");
        HttpServletRequest jakartaRequest = mockJakartaRequest("GET");
        Tag result = HttpJakartaServletRequestTags.method(jakartaRequest);
        assertThat(result).isEqualTo(httpGet);

        javax.servlet.http.HttpServletRequest javaxRequest = mockJavaxRequest("GET");
        result = HttpRequestTags.method(javaxRequest);
        assertThat(result).isEqualTo(httpGet);
    }

    @Test
    void nullResponseShouldContributeUnknownStatusTag() {
        Tag unknownStatus = Tag.of("status", "UNKNOWN");
        HttpServletResponse jakartaResponse = null;
        Tag result = HttpJakartaServletRequestTags.status(jakartaResponse);
        assertThat(result).isEqualTo(unknownStatus);

        javax.servlet.http.HttpServletResponse javaxResponse = null;
        result = HttpRequestTags.status(javaxResponse);
        assertThat(result).isEqualTo(unknownStatus);
    }

    @Test
    void responseShouldContributeStatusTag() {
        Tag httpOk = Tag.of("status", "200");
        HttpServletResponse jakartaResponse = mockJakartaResponse(200);
        Tag result = HttpJakartaServletRequestTags.status(jakartaResponse);
        assertThat(result).isEqualTo(httpOk);

        javax.servlet.http.HttpServletResponse javaxResponse = mockJavaxResponse(200);
        result = HttpRequestTags.status(javaxResponse);
        assertThat(result).isEqualTo(httpOk);
    }

    @Test
    void nullExceptionShouldContributeNoneExceptionTag() {
        Tag exceptionNone = Tag.of("exception", "None");
        Tag result = HttpJakartaServletRequestTags.exception(null);
        assertThat(result).isEqualTo(exceptionNone);
    }

    @Test
    void exceptionShouldContributeExceptionTag() {
        Tag illegalStateTag = Tag.of("exception", "IllegalStateException");
        Tag result = HttpJakartaServletRequestTags.exception(new IllegalStateException());
        assertThat(result).isEqualTo(illegalStateTag);
    }

    @Test
    void nullResponseShouldContributeUnknownOutcomeTag() {
        Tag unknownOutcome = Tag.of("outcome", "UNKNOWN");
        HttpServletResponse jakartaResponse = null;
        Tag result = HttpJakartaServletRequestTags.outcome(jakartaResponse);
        assertThat(result).isEqualTo(unknownOutcome);

        javax.servlet.http.HttpServletResponse javaxResponse = null;
        result = HttpRequestTags.outcome(javaxResponse);
        assertThat(result).isEqualTo(unknownOutcome);
    }

    @Test
    void responseShouldContributeOutcomeTag() {
        Tag successOutcome = Tag.of("outcome", "SUCCESS");
        HttpServletResponse jakartaResponse = mockJakartaResponse(200);
        Tag result = HttpJakartaServletRequestTags.outcome(jakartaResponse);
        assertThat(result).isEqualTo(successOutcome);

        javax.servlet.http.HttpServletResponse javaxResponse = mockJavaxResponse(200);
        result = HttpRequestTags.outcome(javaxResponse);
        assertThat(result).isEqualTo(successOutcome);
    }

    private HttpServletRequest mockJakartaRequest(String method) {
        HttpServletRequest jakartaRequest = mock(HttpServletRequest.class);
        when(jakartaRequest.getMethod()).thenReturn(method);
        return jakartaRequest;
    }

    private javax.servlet.http.HttpServletRequest mockJavaxRequest(String method) {
        javax.servlet.http.HttpServletRequest javaxRequest = mock(javax.servlet.http.HttpServletRequest.class);
        when(javaxRequest.getMethod()).thenReturn(method);
        return javaxRequest;
    }

    private HttpServletResponse mockJakartaResponse(int status) {
        HttpServletResponse jakartaResponse = mock(HttpServletResponse.class);
        when(jakartaResponse.getStatus()).thenReturn(status);
        return jakartaResponse;
    }

    private javax.servlet.http.HttpServletResponse mockJavaxResponse(int status) {
        javax.servlet.http.HttpServletResponse javaxResponse = mock(javax.servlet.http.HttpServletResponse.class);
        when(javaxResponse.getStatus()).thenReturn(status);
        return javaxResponse;
    }

}
