/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.common.KeyValue;
import org.glassfish.jersey.server.ContainerRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JerseyKeyValues}.
 */
class JerseyKeyValuesTests {

    @Test
    void nullRequestShouldResultInUnknownMethod() {
        assertThat(JerseyKeyValues.method(null)).isEqualTo(KeyValue.of("method", "UNKNOWN"));
    }

    @Test
    void unknownMethodShouldResultInUnknownTagValue() {
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getMethod()).thenReturn("WELL");
        assertThat(JerseyKeyValues.method(request)).isEqualTo(KeyValue.of("method", "UNKNOWN"));
    }

    @Test
    void knownMethodShouldResultInTagValue() {
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getMethod()).thenReturn("GET");
        assertThat(JerseyKeyValues.method(request)).isEqualTo(KeyValue.of("method", "GET"));
    }

}
