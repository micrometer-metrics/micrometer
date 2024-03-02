/*
 * Copyright 2024 the original author or authors.
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
package io.micrometer.core.instrument.kotlin

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.assertj.core.api.BDDAssertions.assertThat
import org.assertj.core.api.BDDAssertions.catchException
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.isA
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ObserveAndGetKtTests {

    private val observationHandler = mock(ObservationHandler::class.java).also { handler ->
        `when`(handler.supportsContext(isA(Observation.Context::class.java)))
            .thenReturn(true)
    }

    private val observationRegistry = ObservationRegistry.create().apply {
        observationConfig().observationHandler(observationHandler)
    }

    @Test
    fun `should start and stop both observation and scope when block is executed successfully`() {
        val nonNullValue = "computed value"

        val result = Observation.createNotStarted("observeAndGet", observationRegistry).observeAndGet {
            nonNullValue
        }

        then(result).isSameAs(nonNullValue)
        verify(observationHandler, times(1)).onStart(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onStop(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onScopeOpened(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onScopeClosed(ArgumentMatchers.any())
    }

    @Test
    fun `should start and stop both observation and scope when block throws an exception`() {
        val errorMessage = "Something went wrong"

        val exception = catchException {
            Observation.createNotStarted("observeAndGet", observationRegistry).observeAndGet {
                throw RuntimeException(errorMessage)
            }
        }

        assertThat(exception).hasMessage(errorMessage)
        verify(observationHandler, times(1)).onError(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onStart(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onStop(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onScopeOpened(ArgumentMatchers.any())
        verify(observationHandler, times(1)).onScopeClosed(ArgumentMatchers.any())
    }
}
