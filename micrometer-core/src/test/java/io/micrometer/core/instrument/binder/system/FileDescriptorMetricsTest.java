/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.OperatingSystemMXBean;
import java.util.Locale;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link FileDescriptorMetrics}
 *
 * @author Michael Weirauch
 */
class FileDescriptorMetricsTest {

    private MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void fileDescriptorMetricsUnsupportedOsBeanMock() {
        final OperatingSystemMXBean osBean = mock(UnsupportedOperatingSystemMXBean.class);
        new FileDescriptorMetrics(osBean, Tags.of("some", "tag")).bindTo(registry);

        assertThat(registry.find("process.files.open").gauge()).isNull();
        assertThat(registry.find("process.files.max").gauge()).isNull();
    }

    @Test
    void unixFileDescriptorMetrics() {
        assumeFalse(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

        // tag::example[]
        new FileDescriptorMetrics(Tags.of("some", "tag")).bindTo(registry);

        assertThat(registry.get("process.files.open").tags("some", "tag").gauge().value()).isGreaterThan(0);
        assertThat(registry.get("process.files.max").tags("some", "tag").gauge().value()).isGreaterThan(0);
        // end::example[]
    }

    @Test
    void windowsFileDescriptorMetrics() {
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"));

        new FileDescriptorMetrics(Tags.of("some", "tag")).bindTo(registry);

        assertThat(registry.find("process.files.open").gauge()).isNull();
        assertThat(registry.find("process.files.max").gauge()).isNull();
    }

    /** Represents a JVM implementation we do not currently support. */
    private interface UnsupportedOperatingSystemMXBean extends OperatingSystemMXBean {

        long getOpenFileDescriptorCount();

        long getMaxFileDescriptorCount();

    }

}
