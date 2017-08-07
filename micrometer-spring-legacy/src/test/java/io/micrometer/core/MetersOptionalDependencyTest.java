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
package io.micrometer.core;

import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prove that classes declared in optional dependencies in Meters does not break
 * the class when these dependencies are not on the classpath
 */
@ClassPathExclusions("guava-*.jar")
@RunWith(ModifiedClassPathRunner.class)
public class MetersOptionalDependencyTest {
    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    public void canStillUseOtherMethods() {
        Meters.monitor(registry, Executors.newSingleThreadExecutor(), "executor");
    }

    @Test
    public void cannotCallMethodsInvolvingDependenciesNotOnTheClasspath() {
        assertThatThrownBy(() -> Meters.monitor(registry, CacheBuilder.newBuilder().build(), "cache"))
                .hasCauseInstanceOf(ClassNotFoundException.class);
    }
}
