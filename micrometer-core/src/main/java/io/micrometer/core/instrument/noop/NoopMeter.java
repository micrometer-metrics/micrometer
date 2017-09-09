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
package io.micrometer.core.instrument.noop;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

import java.util.Collections;
import java.util.List;

public abstract class NoopMeter implements Meter {
    @Override
    public Id getId() {
        return new Meter.Id() {
            @Override
            public String getName() {
                return "noop";
            }

            @Override
            public Iterable<Tag> getTags() {
                return Collections.emptyList();
            }

            @Override
            public String getBaseUnit() {
                return null;
            }

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public String getConventionName() {
                return "noop";
            }

            @Override
            public List<Tag> getConventionTags() {
                return Collections.emptyList();
            }

            @Override
            public void setType(Type type) {
            }

            @Override
            public void setBaseUnit(String baseUnit) {

            }
        };
    }

    @Override
    public List<Measurement> measure() {
        return Collections.emptyList();
    }
}
