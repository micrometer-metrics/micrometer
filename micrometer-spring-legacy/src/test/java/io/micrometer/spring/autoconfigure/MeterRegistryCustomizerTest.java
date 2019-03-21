/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class MeterRegistryCustomizerTest {
    @Autowired
    MeterRegistry registry;

    @Test
    public void commonTagsAreAppliedToAutoConfiguredBinders() {
        registry.get("jvm.memory.used").tags("region", "us-east-1").gauge();
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    static class MetricsApp {
        public static void main(String[] args) {
            SpringApplication.run(MetricsApp.class);
        }

        @Bean
        public MeterRegistryCustomizer registryConfigurer() {
            return registry -> registry.config().commonTags("region", "us-east-1");
        }
    }
}
