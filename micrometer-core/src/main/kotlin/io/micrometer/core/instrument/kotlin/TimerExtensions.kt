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
package io.micrometer.core.instrument.kotlin

import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

/**
 * Executes the suspending [block] and records its execution time with this [Timer].
 *
 * The time is measured with the clock of the given [registry], from the call until
 * [block] returns, throws or is cancelled, including any time the coroutine spends
 * suspended. Unlike [Timer.record], [block] may call suspending functions.
 * @param registry the registry whose clock is used to measure the execution time
 * @param block the suspending block to time
 * @return the value returned by [block]
 * @since 1.18.0
 */
suspend fun <T> Timer.recordSuspend(registry: MeterRegistry, block: suspend () -> T): T {
    val sample = Timer.start(registry)
    try {
        return block()
    } finally {
        sample.stop(this)
    }
}

/**
 * Executes the suspending [block] while this [LongTaskTimer] tracks it as an active task.
 *
 * The task is started before [block] is called and stopped when [block] returns, throws
 * or is cancelled. Unlike [LongTaskTimer.record], [block] may call suspending functions.
 * @param block the suspending block to time
 * @return the value returned by [block]
 * @since 1.18.0
 */
suspend fun <T> LongTaskTimer.recordSuspend(block: suspend () -> T): T {
    val sample = start()
    try {
        return block()
    } finally {
        sample.stop()
    }
}
