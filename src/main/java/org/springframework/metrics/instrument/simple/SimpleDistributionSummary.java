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

import org.springframework.metrics.instrument.DistributionSummary;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleDistributionSummary implements DistributionSummary {
    private final String name;
    private AtomicLong count = new AtomicLong(0);
    private double amount = 0.0;

    public SimpleDistributionSummary(String name) {
        this.name = name;
    }

    @Override
    public void record(double amount) {
        count.incrementAndGet();
        amount += amount;
    }

    @Override
    public long count() {
        return count.get();
    }

    @Override
    public double totalAmount() {
        return amount;
    }

    @Override
    public String getName() {
        return name;
    }
}
