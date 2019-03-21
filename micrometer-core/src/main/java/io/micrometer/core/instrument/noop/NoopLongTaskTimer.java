/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.LongTaskTimer;

import java.util.concurrent.TimeUnit;

public class NoopLongTaskTimer extends NoopMeter implements LongTaskTimer {
    public NoopLongTaskTimer(Id id) {
        super(id);
    }

    @Override
    public Sample start() {
        return new Sample(this, 0);
    }

    @Override
    public long stop(long task) {
        return -1;
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        return -1;
    }

    @Override
    public double duration(TimeUnit unit) {
        return 0;
    }

    @Override
    public int activeTasks() {
        return 0;
    }
}
