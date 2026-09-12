/*
 * Copyright 2013-2022 the original author or authors.
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
import io.micrometer.observation.ObservationRegistry
import kotlinx.coroutines.withContext
import java.util.function.Supplier
import kotlin.coroutines.CoroutineContext

/**
 * Returns a [CoroutineContext] which will contain the current [Observation].
 *
 * Inspired by OpenTelemetry's asContextElement.
 * @since 1.10.0
 */
fun ObservationRegistry.asContextElement(): CoroutineContext {
    return KotlinObservationContextElement(this, ContextRegistry.getInstance())
}

/**
 * Returns the [Observation] in this [CoroutineContext] if present, or null otherwise.
 *
 * Inspired by OpenTelemetry's currentObservation.
 * @since 1.10.0
 */
fun CoroutineContext.currentObservation(): Observation? {
    val element = get(KotlinObservationContextElement.KEY)
    if (element is KotlinObservationContextElement) {
        return element.currentObservation
    }
    return null
}

/**
 * Observes the given [block] within an [Observation] named [name].
 *
 * @since 1.18.0
 */
fun <T> ObservationRegistry.observe(name: String, block: () -> T): T {
    return Observation.createNotStarted(name, this).observe(Supplier { block() })
}

/**
 * Observes the given suspending [block] within an [Observation] named [name].
 *
 * @since 1.18.0
 */
suspend fun <T> ObservationRegistry.observeSuspend(name: String, block: suspend () -> T): T {
    return Observation.createNotStarted(name, this).observeSuspend(block)
}

/**
 * Suspending equivalent of [Observation.observe], keeping this [Observation] current across
 * coroutine suspension and resumption.
 *
 * @since 1.18.0
 */
suspend fun <T> Observation.observeSuspend(block: suspend () -> T): T {
    start()
    return try {
        openScope().use {
            withContext(observationRegistry.asContextElement()) {
                block()
            }
        }
    } catch (error: Throwable) {
        error(error)
        throw error
    } finally {
        stop()
    }
}
