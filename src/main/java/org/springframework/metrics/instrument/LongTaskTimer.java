/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface LongTaskTimer extends Meter {
    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    default <T> T recordCallable(Callable<T> f) throws Exception {
        long id = start();
        try {
            return f.call();
        } finally {
            stop(id);
        }
    }

    /**
     * Executes the callable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     * @return The return value of `f`.
     */
    default <T> T record(Supplier<T> f) throws Exception {
        long id = start();
        try {
            return f.get();
        } finally {
            stop(id);
        }
    }

    /**
     * Executes the runnable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time with a reference to the
     *          timer id useful for looking up current duration.
     */
    default void record(Consumer<Long> f) {
        long id = start();
        try {
            f.accept(id);
        } finally {
            stop(id);
        }
    }

    /**
     * Executes the runnable `f` and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     */
    default void record(Runnable f) {
        long id = start();
        try {
            f.run();
        } finally {
            stop(id);
        }
    }

    /**
     * Start keeping time for a task.
     *
     * @return A task id that can be used to look up how long the task has been running.
     */
    long start();

    /**
     * Mark a given task as completed.
     *
     * @param task Id for the task to stop. This should be the value returned from {@link #start()}.
     * @return Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
     */
    long stop(long task);

    /**
     * Returns the current duration for an active task.
     *
     * @param task Id for the task to stop. This should be the value returned from {@link #start()}.
     * @return Duration for the task in nanoseconds. A -1 value will be returned for an unknown task.
     */
    long duration(long task);

    /**
     * Returns the cumulative duration of all current tasks in nanoseconds.
     */
    long duration();

    /**
     * Returns the current number of tasks being executed.
     */
    int activeTasks();

    @Override
    default Type getType() {
        return Type.LongTaskTimer;
    }
}
