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
