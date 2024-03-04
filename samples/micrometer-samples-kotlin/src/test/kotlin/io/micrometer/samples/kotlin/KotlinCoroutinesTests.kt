/*
 * Copyright 2024 VMware, Inc.
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
package io.micrometer.samples.kotlin

import io.micrometer.core.instrument.kotlin.asContextElement
import io.micrometer.core.instrument.kotlin.currentObservation
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.*
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test

internal class KotlinCoroutinesTests {

    val observationRegistry = ObservationRegistry.create()

    @Test
    fun `should return current observation from context`(): Unit = runBlocking {
        observationRegistry.observationConfig().observationHandler { true }
        val nextObservation = Observation.start("name", observationRegistry)
        val inScope = nextObservation.openScope()
        var observationInGlobalScopeLaunch: Observation? = null
        var observationInGlobalScopeAsync: Observation? = null
        val asContextElement = observationRegistry.asContextElement()

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(asContextElement) {
            observationInGlobalScopeLaunch = coroutineContext.currentObservation()
        }
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.async(asContextElement) {
            observationInGlobalScopeAsync = coroutineContext.currentObservation()
        }.await()

        inScope.close()

        then(observationInGlobalScopeLaunch).isSameAs(nextObservation)
        then(observationInGlobalScopeAsync).isSameAs(nextObservation)
    }
}
