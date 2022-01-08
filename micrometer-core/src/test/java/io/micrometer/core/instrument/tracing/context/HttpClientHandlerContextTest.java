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
package io.micrometer.core.instrument.tracing.context;

import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link HttpClientHandlerContext}.
 *
 * @author Jonatan Ivanov
 */
class HttpClientHandlerContextTest {

    @Test
    void gettersAndSettersShouldWork() {
        HttpClientRequest request = mock(HttpClientRequest.class);
        HttpClientResponse response = mock(HttpClientResponse.class);
        verifyNoMoreInteractions(request, response);

        HttpClientHandlerContext context = new HttpClientHandlerContext(request);
        assertThat(context.getRequest()).isSameAs(request);
        assertThat(context.getResponse()).isNull();

        assertThat(context.setResponse(response)).isSameAs(context);
        assertThat(context.getResponse()).isSameAs(response);
    }
}
