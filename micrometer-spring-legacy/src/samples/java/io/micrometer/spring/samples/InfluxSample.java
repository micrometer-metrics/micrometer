package io.micrometer.spring.samples;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.micrometer.spring.samples.components")
@EnableScheduling
public class InfluxSample {
    public static void main(String[] args) {
        new SpringApplicationBuilder(AtlasSample.class).profiles("influx").run(args);
    }
}
