package org.springframework.metrics.instrument.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.metrics.boot.EnableMetrics;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class MetricsSchedulingAspectTest {

    @Autowired
    MeterRegistry registry;

    @Test
    void scheduledIsInstrumented() {
        assertThat(registry.findMeter(Timer.class, "beeper"))
                .containsInstanceOf(Timer.class)
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));
    }

    @SpringBootApplication
    @EnableMetrics
    @EnableScheduling
    static class MetricsApp {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Timed("beeper")
        @Scheduled(fixedRate = 1000)
        void beep1() {
            System.out.println("beep");
        }

        @Timed // not instrumented because @Timed lacks a metric name
        @Scheduled(fixedRate = 1000)
        void beep2() {
            System.out.println("beep");
        }

        @Scheduled(fixedRate = 1000) // not instrumented because it isn't @Timed
        void beep3() {
            System.out.println("beep");
        }
    }
}
