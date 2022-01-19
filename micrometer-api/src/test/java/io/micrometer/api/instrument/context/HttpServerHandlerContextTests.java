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
package io.micrometer.api.instrument.context;

import io.micrometer.api.instrument.transport.http.HttpServerRequest;
import io.micrometer.api.instrument.transport.http.HttpServerResponse;
import io.micrometer.api.instrument.transport.http.context.HttpServerHandlerContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link HttpServerHandlerContext}.
 *
 * @author Jonatan Ivanov
 */
class HttpServerHandlerContextTests {

    @Test
    void gettersAndSettersShouldWork() {
        HttpServerRequest request = mock(HttpServerRequest.class);
        HttpServerResponse response = mock(HttpServerResponse.class);

        HttpServerHandlerContext context = new HttpServerHandlerContext(request);
        assertThat(context.getRequest()).isSameAs(request);
        assertThat(context.getResponse()).isNull();

        assertThat(context.setResponse(response)).isSameAs(context);
        assertThat(context.getResponse()).isSameAs(response);
    }
}
