/**
 * Copyright 2020 Pivotal Software, Inc.
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
package io.micrometer.core.instrument;

import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.util.MeterEquivalence;

public abstract class AbstractLongTaskTimer extends AbstractMeter implements LongTaskTimer {

    private final TimeUnit baseTimeUnit;
    
    /**
     * Creates a new long task timer.
     *
     * @param id                            The timer's name and tags.
     * @param clock                         The clock used to measure latency.
     * @param baseTimeUnit                  The time scale of this timer.
     */
    public AbstractLongTaskTimer(Id id, TimeUnit baseTimeUnit) {
        super(id);
        this.baseTimeUnit = baseTimeUnit;
    }
    
    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }
    
    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

}
