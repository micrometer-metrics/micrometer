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
package io.micrometer.spring.autoconfigure;

import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = {
        "management.metrics.export.prometheus.enabled=true",
        "management.metrics.export.atlas.enabled=true"
})
public class MeterRegistryCustomizerTest {
    @Autowired
    AtlasMeterRegistry atlasRegistry;

    @Autowired
    PrometheusMeterRegistry prometheusRegistry;

    @Test
    public void commonTagsAreAppliedToAutoConfiguredBinders() {
        atlasRegistry.get("jvm.memory.used").tags("region", "us-east-1").gauge();
        prometheusRegistry.get("jvm.memory.used").tags("region", "us-east-1").gauge();

        assertThat(atlasRegistry.find("jvm.memory.used").tags("job", "myjob").gauge()).isNull();
        prometheusRegistry.get("jvm.memory.used").tags("job", "myjob").gauge();
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    static class MetricsApp {
        @Bean
        public MeterRegistryCustomizer<MeterRegistry> commonTags() {
            return registry -> registry.config().commonTags("region", "us-east-1");
        }

        @Bean
        public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusOnlyCommonTags() {
            return registry -> registry.config().commonTags("job", "myjob");
        }
    }
}
