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
package io.micrometer.spring.samples;

import io.micrometer.spring.autoconfigure.MeterRegistryConfigurer;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.micrometer.spring.samples.components")
@EnableScheduling
public class PrometheusSample {
    public static void main(String[] args) {
        new SpringApplicationBuilder(PrometheusSample.class).profiles("prometheus").run(args);
    }

    @Bean
    public MeterRegistryConfigurer addLogging(Tracer tracer){
        return registry -> {
            registry.config().setTracer(tracer);
        };
    }

    @Bean
    public Tracer tracer() {
        Tracer tracer = new com.uber.jaeger.Configuration(
            "prometheusSample",
            new com.uber.jaeger.Configuration.SamplerConfiguration("const", 1),
            new com.uber.jaeger.Configuration.ReporterConfiguration(
                true,  // logSpans
                "localhost",
                5775,
                1000,   // flush interval in milliseconds
                10000)  // max buffered Spans
        ).getTracer();

        GlobalTracer.register(tracer);

//        Configuration.ReporterConfiguration reporterConfiguration = new Configuration.ReporterConfiguration(true, "localhost", 5775, null, null);
//        Configuration config = new Configuration("prometheusSample", null, reporterConfiguration);
////        config.setStatsFactory(...); // optional if you want to get metrics about setTracer behavior



        return tracer;
    }

}
