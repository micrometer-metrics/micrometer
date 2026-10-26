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
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.*
import org.assertj.core.api.BDDAssertions.then
import org.assertj.core.api.BDDAssertions.thenThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class TimerExtensionsKtTests {

    val clock = MockClock()

    val registry = SimpleMeterRegistry(SimpleConfig.DEFAULT, clock)

    val timer: Timer = Timer.builder("my.timer").register(registry)

    @Test
    fun `should record suspending block`(): Unit = runBlocking {
        val result = timer.recordSuspend(registry) {
            suspendingWork(Duration.ofMillis(100))
            "done"
        }

        then(result).isEqualTo("done")
        then(timer.count()).isEqualTo(1)
        then(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100.0)
    }

    @Test
    fun `should record suspending block that switches threads`(): Unit = runBlocking {
        val callerThread = Thread.currentThread()
        var blockThread: Thread? = null

        timer.recordSuspend(registry) {
            withContext(Dispatchers.Default) {
                blockThread = Thread.currentThread()
                suspendingWork(Duration.ofMillis(50))
            }
            suspendingWork(Duration.ofMillis(25))
        }

        then(blockThread).isNotSameAs(callerThread)
        then(timer.count()).isEqualTo(1)
        then(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(75.0)
    }

    @Test
    fun `should record suspending block that throws`(): Unit = runBlocking {
        thenThrownBy {
            runBlocking {
                timer.recordSuspend(registry) {
                    suspendingWork(Duration.ofMillis(10))
                    throw IllegalStateException("boom")
                }
            }
        }.isInstanceOf(IllegalStateException::class.java).hasMessage("boom")

        then(timer.count()).isEqualTo(1)
        then(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(10.0)
    }

    @Test
    fun `should record suspending block that is cancelled`(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            timer.recordSuspend(registry) {
                clock.add(Duration.ofMillis(20))
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        job.cancelAndJoin()

        then(job.isCancelled).isTrue()
        then(timer.count()).isEqualTo(1)
        then(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(20.0)
    }

    @Test
    fun `should track suspending block as active task of long task timer`(): Unit = runBlocking {
        val longTaskTimer = LongTaskTimer.builder("my.long.task.timer").register(registry)
        var activeTasksInBlock = -1
        var durationInBlock = -1.0

        val result = longTaskTimer.recordSuspend {
            withContext(Dispatchers.Default) {
                suspendingWork(Duration.ofMillis(100))
            }
            activeTasksInBlock = longTaskTimer.activeTasks()
            durationInBlock = longTaskTimer.duration(TimeUnit.MILLISECONDS)
            "done"
        }

        then(result).isEqualTo("done")
        then(activeTasksInBlock).isEqualTo(1)
        then(durationInBlock).isEqualTo(100.0)
        then(longTaskTimer.activeTasks()).isZero()
    }

    @Test
    fun `should stop long task timer task when suspending block throws`(): Unit = runBlocking {
        val longTaskTimer = LongTaskTimer.builder("my.long.task.timer").register(registry)

        thenThrownBy {
            runBlocking {
                longTaskTimer.recordSuspend {
                    yield()
                    throw IllegalStateException("boom")
                }
            }
        }.isInstanceOf(IllegalStateException::class.java)

        then(longTaskTimer.activeTasks()).isZero()
    }

    private suspend fun suspendingWork(duration: Duration) {
        yield()
        clock.add(duration)
    }
}
