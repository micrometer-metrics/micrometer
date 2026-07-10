/*
 * Copyright 2025 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument;

import com.sun.management.ThreadMXBean;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import java.lang.management.ManagementFactory;

import static io.micrometer.core.instrument.MeterRegistryAllocationTest.JAVA_VM_NAME_J9_REGEX;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test expected allocation (or lack thereof) for methods expected to be used on hot paths
 * from {@link MeterRegistry}.
 */
@DisabledIfSystemProperty(named = "java.vm.name", matches = JAVA_VM_NAME_J9_REGEX,
        disabledReason = "Sun ThreadMXBean with allocation counter not available on J9 JVM")
class MeterRegistryAllocationTest {

    // Should match "Eclipse OpenJ9 VM" and "IBM J9 VM"
    static final String JAVA_VM_NAME_J9_REGEX = ".*J9 VM$";

    MeterRegistry registry = new SimpleMeterRegistry();

    Tags tags = Tags.of("a", "b", "c", "d");

    static ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    /**
     * Number of bytes allocated when constructing a Meter.Id. This can vary by JVM and
     * configuration, which is why we measure it programmatically. For most common cases,
     * it should be 40 bytes.
     */
    static long newIdBytes;

    @BeforeAll
    static void setup() {
        // Construct once here so any static initialization doesn't get measured.
        new Meter.Id("name", Tags.empty(), null, null, Meter.Type.OTHER);
        newIdBytes = measureAllocatedBytes(() -> new Meter.Id("name", Tags.empty(), null, null, Meter.Type.OTHER));
    }

    private static long measureAllocatedBytes(Runnable runnable) {
        long currentThreadId = Thread.currentThread().getId();
        long allocatedBytesBefore = threadMXBean.getThreadAllocatedBytes(currentThreadId);

        runnable.run();

        return threadMXBean.getThreadAllocatedBytes(currentThreadId) - allocatedBytesBefore;
    }

    @BeforeEach
    void setUp() {
        registry.timer("timer", tags);
        LongTaskTimer.builder("ltt").tags(tags).register(registry);
        Counter.builder("counter").tags(tags).register(registry);
    }

    @Nested
    @DisplayName("Internal implementation details")
    class Internal {

        @Issue("#6670")
        @Test
        void retrieveExistingTimer_noAllocation() {
            Meter.Id id = new Meter.Id("timer", tags, null, null, Meter.Type.TIMER);
            assertNoAllocation(() -> registry.timer(id, AbstractTimerBuilder.DEFAULT_DISTRIBUTION_CONFIG,
                    NoPauseDetector.INSTANCE));
        }

        @Issue("#6670")
        @Test
        void retrieveExistingLongTaskTimer_noAllocation() {
            Meter.Id id = new Meter.Id("ltt", tags, null, null, Meter.Type.LONG_TASK_TIMER);
            assertNoAllocation(
                    () -> registry.more().longTaskTimer(id, LongTaskTimer.Builder.DEFAULT_DISTRIBUTION_CONFIG));
        }

        @Issue("#6670")
        @Test
        void retrieveExistingCounter_noAllocation() {
            Meter.Id id = new Meter.Id("counter", tags, null, null, Meter.Type.COUNTER);
            assertNoAllocation(() -> registry.counter(id));
        }

    }

    @Nested
    @DisplayName("Public API available to users")
    class PublicApi {

        @Issue("#6670")
        @Test
        void retrieveExistingTimer_onlyIdAllocation() {
            assertBytesAllocated(newIdBytes, () -> registry.timer("timer", tags));
        }

        @Issue("#6670")
        @Test
        void retrieveExistingLongTaskTimer_onlyIdAllocation() {
            assertBytesAllocated(newIdBytes, () -> registry.more().longTaskTimer("ltt", tags));
        }

        @Issue("#6670")
        @Test
        void retrieveExistingCounter_onlyIdAllocation() {
            assertBytesAllocated(newIdBytes, () -> registry.counter("counter", tags));
        }

    }

    private void assertBytesAllocated(long bytes, Runnable runnable) {
        assertThat(measureAllocatedBytes(runnable)).isEqualTo(bytes);
    }

    private void assertNoAllocation(Runnable runnable) {
        assertBytesAllocated(0, runnable);
    }

}
