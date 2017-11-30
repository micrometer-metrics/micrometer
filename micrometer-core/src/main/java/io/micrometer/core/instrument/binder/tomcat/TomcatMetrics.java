/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.binder.tomcat;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.catalina.Manager;

import java.util.concurrent.TimeUnit;

public class TomcatMetrics implements MeterBinder {
    private final Manager manager;

    private Iterable<Tag> tags;

    public static void monitor(MeterRegistry meterRegistry, Manager manager, String... tags) {
        monitor(meterRegistry, manager, Tags.zip(tags));
    }

    public static void monitor(MeterRegistry meterRegistry, Manager manager, Iterable<Tag> tags) {
        new TomcatMetrics(manager, tags).bindTo(meterRegistry);
    }

    public TomcatMetrics(Manager manager, Iterable<Tag> tags) {
        this.tags = tags;
        this.manager = manager;
    }

    @Override
    public void bindTo(MeterRegistry reg) {
        if(manager == null){
            //If the binder is created but unable to find the session manager don't register anything
            return;
        }

        Gauge.builder("tomcat.sessions.active.max", manager, Manager::getMaxActive)
            .tags(tags)
            .register(reg);
        Gauge.builder("tomcat.sessions.active.current", manager, Manager::getActiveSessions)
            .tags(tags)
            .register(reg);
        Gauge.builder("tomcat.sessions.expired", manager, Manager::getExpiredSessions)
            .tags(tags)
            .register(reg);
        Gauge.builder("tomcat.sessions.rejected", manager, Manager::getRejectedSessions)
            .tags(tags)
            .register(reg);
        TimeGauge.builder("tomcat.sessions.alive.max", manager, TimeUnit.SECONDS, Manager::getSessionMaxAliveTime)
            .tags(tags)
            .register(reg);

        FunctionCounter.builder("tomcat.sessions.created", manager, Manager::getSessionCounter)
            .tags(tags)
            .register(reg);
    }
}
