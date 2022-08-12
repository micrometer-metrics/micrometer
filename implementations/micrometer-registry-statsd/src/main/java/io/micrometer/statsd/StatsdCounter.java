/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;

import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;

/**
 * @author Jon Schneider
 */
public class StatsdCounter extends AbstractMeter implements Counter {

    private final StatsdLineBuilder lineBuilder;

    private final Consumer<String> sink;

    private DoubleAdder count = new DoubleAdder();

    private volatile boolean shutdown;

    StatsdCounter(Id id, StatsdLineBuilder lineBuilder, Consumer<String> sink) {
        super(id);
        this.lineBuilder = lineBuilder;
        this.sink = sink;
    }

    @Override
    public void increment(double amount) {
        if (!shutdown && amount > 0) {
            count.add(amount);
            sink.accept(lineBuilder.count((long) amount));
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
    }

    void shutdown() {
        this.shutdown = true;
    }

}
