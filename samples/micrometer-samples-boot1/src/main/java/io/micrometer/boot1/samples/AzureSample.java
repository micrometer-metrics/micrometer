package io.micrometer.boot1.samples;

import io.micrometer.boot1.samples.components.PersonController;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = PersonController.class)
@EnableScheduling
public class AzureSample {

    public static void main(String[] args) {
        new SpringApplicationBuilder(AtlasSample.class).profiles("azure").run(args);
    }
}
