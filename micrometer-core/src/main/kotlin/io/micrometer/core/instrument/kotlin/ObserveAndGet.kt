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

/**
 * Observes the provided block of code, which means the following:
 * - Starts the [Observation]
 * - Opens a scope
 * - Calls the provided [block]
 * - Closes the scope
 * - Signals the error to the [Observation] if any
 * - Stops the [Observation]
 *
 * For a suspending version, see [Observation.observeAndAwait]
 *
 * @param block the block of code to be observed
 * @return the result of executing the provided block of code
 */
fun <T> Observation.observeAndGet(block: () -> T): T {
    start()
    return try {
        openScope().use { block() }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
