/**
 * Copyright 2019 VMware, Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@GcTest
class JvmMemoryTest {
    @Test
    @DisabledIfSystemProperty(named = "java.vm.vendor", matches = "Eclipse OpenJ9")
    void getLongLivedHeapPool() {
        assertThat(JvmMemory.getLongLivedHeapPools()).isNotEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = "java.vm.vendor", matches = "Eclipse OpenJ9")
    void getLongLivedHeapPoolWithEclipseOpenJ9() {
        assertThat(JvmMemory.getLongLivedHeapPools()).isEmpty();
    }

    @Test
    void assertTolerateNullName() {
        // There is a way for the name passed to these methods to be null.
        // Ensure they don't fail;
        assertThat(JvmMemory.isLongLivedPool(null)).isFalse();
        assertThat(JvmMemory.isAllocationPool(null)).isFalse();
    }
}
