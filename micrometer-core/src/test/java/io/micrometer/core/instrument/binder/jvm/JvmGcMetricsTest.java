/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.lang.management.MemoryPoolMXBean;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link JvmGcMetrics}.
 *
 * @author Johnny Lim
 */
@GcTest
class JvmGcMetricsTest {

    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void beforeEach() {
        new JvmGcMetrics().bindTo(registry);
    }

    @Test
    void noJvmImplementationSpecificApiSignatures() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages("io.micrometer.core.instrument.binder.jvm");

        ArchRule noSunManagementInMethodSignatures = methods()
                .should().notHaveRawReturnType(resideInAPackage("com.sun.management.."))
                .andShould().notHaveRawParameterTypes(DescribedPredicate.anyElementThat(resideInAPackage("com.sun.management..")));

        noSunManagementInMethodSignatures.check(importedClasses);
    }

    @Test
    void metersAreBound() {
        assertThat(registry.find("jvm.gc.live.data.size").gauge()).isNotNull();
        assertThat(registry.find("jvm.gc.memory.allocated").counter()).isNotNull();
        assertThat(registry.find("jvm.gc.max.data.size").gauge().value()).isGreaterThan(0);

        assumeTrue(isGenerationalGc());
        assertThat(registry.find("jvm.gc.memory.promoted").counter()).isNotNull();
    }

    private boolean isGenerationalGc() {
        return JvmMemory.getLongLivedHeapPool().map(MemoryPoolMXBean::getName).filter(JvmMemory::isOldGenPool).isPresent();
    }

}
