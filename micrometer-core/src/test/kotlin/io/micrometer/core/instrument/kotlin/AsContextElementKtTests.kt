/*
 * Copyright 2013-2021 the original author or authors.
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

import io.micrometer.context.ContextRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.BDDAssertions.then
import org.junit.jupiter.api.Test

internal class AsContextElementKtTests {

	@Test
	fun `should return current observation from context`(): Unit = runBlocking {
		val observationRegistry = ObservationRegistry.create()
		observationRegistry.observationConfig().observationHandler { true }
		val nextObservation = Observation.start("name", observationRegistry)
		val inScope = nextObservation.openScope()
		var observationInGlobalScopeLaunch: Observation? = null
		var observationInGlobalScopeAsync: Observation? = null
		val asContextElement = observationRegistry.asContextElement()

		GlobalScope.launch(asContextElement) {
			observationInGlobalScopeLaunch = coroutineContext.currentObservation()
		}
		GlobalScope.async(asContextElement) {
			observationInGlobalScopeAsync = coroutineContext.currentObservation()
		}.await()

		inScope.close();

		then(observationInGlobalScopeLaunch).isSameAs(nextObservation)
		then(observationInGlobalScopeAsync).isSameAs(nextObservation)
	}

	@Test
	fun `should return observation from coroutine context when KotlinContextElement present`(): Unit = runBlocking {
		val observationRegistry = ObservationRegistry.create()
		observationRegistry.observationConfig().observationHandler { true }
		val nextObservation = Observation.start("name", observationRegistry)
		val inScope = nextObservation.openScope()
		val element = KotlinObservationContextElement(observationRegistry, ContextRegistry.getInstance())

		then(element.currentObservation()).isSameAs(nextObservation)
		inScope.close()
	}


}
