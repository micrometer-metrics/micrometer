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
package io.micrometer.core.instrument.jmx;

import com.codahale.metrics.JmxReporter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.IdentityTagFormatter;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

public class JmxMeterRegistry extends DropwizardMeterRegistry {
    private final JmxReporter reporter;

    public JmxMeterRegistry() {
        this(HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public JmxMeterRegistry(HierarchicalNameMapper nameMapper, Clock clock) {
        super(nameMapper, clock, new IdentityTagFormatter());

        this.reporter = JmxReporter.forRegistry(getDropwizardRegistry()).build();
        this.reporter.start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start();
    }
}
