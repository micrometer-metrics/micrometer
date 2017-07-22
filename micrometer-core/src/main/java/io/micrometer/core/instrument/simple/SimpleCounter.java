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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.util.MeterId;

import java.util.Collections;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class SimpleCounter extends AbstractSimpleMeter implements Counter {
    private DoubleAdder count = new DoubleAdder();

    public SimpleCounter(MeterId id) {
        super(id);
    }

    @Override
    public void increment() {
        count.add(1.0);
    }

    @Override
    public void increment(double amount) {
        count.add(amount);
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    @Override
    public Iterable<Measurement> measure() {
        return Collections.singletonList(id.withTags(SimpleUtils.typeTag(getType())).measurement(count()));
    }
}
