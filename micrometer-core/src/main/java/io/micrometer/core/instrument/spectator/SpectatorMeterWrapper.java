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
package io.micrometer.core.instrument.spectator;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.Tag;

import java.util.List;

/**
 * A Micrometer Meter that wraps an arbitrary Spectator Meter.
 *
 * @author Jon Schneider
 */
public class SpectatorMeterWrapper implements Meter {
    private final String name;
    private final Iterable<Tag> tags;
    private final Type type;
    private final com.netflix.spectator.api.Meter spectatorCounter;

    public SpectatorMeterWrapper(String name, Iterable<Tag> tags, Type type, com.netflix.spectator.api.Meter spectatorCounter) {
        this.name = name;
        this.tags = tags;
        this.type = type;
        this.spectatorCounter = spectatorCounter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Iterable<Tag> getTags() {
        return tags;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public List<Measurement> measure() {
        return SpectatorUtils.measurements(spectatorCounter);
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
