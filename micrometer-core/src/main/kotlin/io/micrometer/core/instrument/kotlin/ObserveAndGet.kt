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
import io.micrometer.observation.Observation.Context
import io.micrometer.observation.ObservationRegistry

/**
 * Observes the provided block of code, which means the following:
 * - Creates and starts an [Observation]
 * - Opens a scope
 * - Calls the provided [block]
 * - Closes the scope
 * - Signals the error to the [Observation] if any
 * - Stops the [Observation]
 *
 * For a suspending version, see [ObservationRegistry.observeAndAwait]
 *
 * @param name name for the observation
 * @param contextSupplier supplier of the context for the observation
 * @param block the block of code to be observed
 * @return the result of executing the provided block of code
 */
fun <T> ObservationRegistry.observeAndGet(
    name: String,
    contextSupplier: () -> Context = { Context() },
    block: () -> T,
): T = Observation.start(name, contextSupplier, this).run {
    try {
        return openScope().use { block() }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
