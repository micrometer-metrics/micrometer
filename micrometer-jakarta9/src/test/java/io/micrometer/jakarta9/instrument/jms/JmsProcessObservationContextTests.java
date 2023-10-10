/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.jakarta9.instrument.jms;

import jakarta.jms.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link JmsProcessObservationContext}.
 *
 * @author Brian Clozel
 */
class JmsProcessObservationContextTests {

    @ParameterizedTest
    @MethodSource("headerValues")
    void propagatorShouldEscapeHeader(String value, String escaped) throws Exception {

        Message sendMessage = mock(Message.class);
        JmsProcessObservationContext context = new JmsProcessObservationContext(sendMessage);
        context.getGetter().get(sendMessage, value);

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(sendMessage).getStringProperty(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue()).isEqualTo(escaped);
    }

    static Stream<Arguments> headerValues() {
        return Stream.of(Arguments.of("valid", "valid"), Arguments.of("with-hyphen", "with_HYPHEN_hyphen"),
                Arguments.of("with.dot", "with_DOT_dot"));
    }

}
