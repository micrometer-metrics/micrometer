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
package io.micrometer.spring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = "metrics.useGlobalRegistry=false")
public class MeterRegistryConfigurerTest {

    @Autowired
    MeterRegistry registry;

    @Test
    public void commonTagsAreAppliedToAutoConfiguredBinders() {
        Optional<Gauge> memUsed = registry.find("jvm.memory.used").gauge();
        assertThat(memUsed).hasValueSatisfying(g -> assertThat(g.getTags()).contains(Tag.of("region", "us-east-1")));
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    static class MetricsApp {
        public static void main(String[] args) {
            SpringApplication.run(MetricsApp.class);
        }

        @Bean
        public MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public MeterRegistryConfigurer registryConfigurer() {
            return registry -> registry.config().commonTags("region", "us-east-1");
        }
    }
}
