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
package org.springframework.metrics.instrument.simple;

import com.netflix.spectator.impl.AtomicDouble;
import org.springframework.metrics.instrument.Counter;

/**
 * @author Jon Schneider
 */
public class SimpleCounter implements Counter {
    private final String name;
    private AtomicDouble count = new AtomicDouble(0);

    public SimpleCounter(String name) {
        this.name = name;
    }

    @Override
    public void increment() {
        count.addAndGet(1.0);
    }

    @Override
    public void increment(double amount) {
        count.addAndGet(amount);
    }

    @Override
    public double count() {
        return count.get();
    }

    @Override
    public String getName() {
        return name;
    }
}
