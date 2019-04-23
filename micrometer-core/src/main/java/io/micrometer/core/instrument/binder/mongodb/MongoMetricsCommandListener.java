/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.mongodb;

import com.mongodb.MongoClient;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.concurrent.TimeUnit;

/**
 * {@link CommandListener} for collecting command metrics from {@link MongoClient}.
 *
 * @author Christophe Bornet
 */
@NonNullApi
@NonNullFields
public class MongoMetricsCommandListener implements CommandListener {

    private static final String DEFAULT_METRICS_NAME = "org.mongodb.driver.commands";
    private final Timer.Builder timerBuilder;

    private final MeterRegistry registry;

    public MongoMetricsCommandListener(MeterRegistry registry) {
        this(registry, DEFAULT_METRICS_NAME);
    }

    public MongoMetricsCommandListener(MeterRegistry registry, String metricsName) {
        this.registry = registry;
        this.timerBuilder = Timer.builder(metricsName)
                .description("Timer of mongodb commands");
    }

    @Override
    public void commandStarted(CommandStartedEvent commandStartedEvent) {
        // NoOp
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        timerBuilder
                .tag("command", event.getCommandName())
                .tag("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue())
                .tag("server.address", event.getConnectionDescription().getServerAddress().toString())
                .tag("status", "SUCCESS")
                .register(registry)
                .record(event.getElapsedTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        timerBuilder
                .tag("command", event.getCommandName())
                .tag("cluster.id", event.getConnectionDescription().getConnectionId().getServerId().getClusterId().getValue())
                .tag("server.address", event.getConnectionDescription().getServerAddress().toString())
                .tag("status", "FAILED")
                .register(registry)
                .record(event.getElapsedTime(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
    }
}

