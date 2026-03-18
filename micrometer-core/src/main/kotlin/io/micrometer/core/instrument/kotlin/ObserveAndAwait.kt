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
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Observes the provided **suspending** block of code, which means the following:
 * - Starts the [Observation]
 * - Puts the [Observation] into [CoroutineContext]
 * - Calls the provided [block] withing the augmented [CoroutineContext]
 * - Signals the error to the [Observation] if any
 * - Stops the [Observation]
 *
 * @param block the block of code to be observed
 * @return the result of executing the provided block of code
 */
suspend fun <T> Observation.observeAndAwait(block: suspend () -> T): T {
    start()
    return try {
        withContext(
            openScope().use { observationRegistry.asContextElement() },
        ) {
            block()
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
