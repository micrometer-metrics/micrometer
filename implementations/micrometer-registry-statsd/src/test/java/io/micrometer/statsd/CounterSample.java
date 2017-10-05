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
package io.micrometer.statsd;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class CounterSample {
    public static void main(String[] args) {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.DEBUG);

        StatsdMeterRegistry registry = new StatsdMeterRegistry(new StatsdConfig() {
            @Override
            public String get(String k) {
                return null;
            }

            @Override
            public StatsdFlavor flavor() {
                return StatsdFlavor.Telegraf;
            }
        }, Clock.SYSTEM);

        Counter counter = registry.counter("my.counter", "k", "v");

        Flux.interval(Duration.ofSeconds(1))
            .doOnEach(s -> counter.increment())
            .blockLast();
    }
}
