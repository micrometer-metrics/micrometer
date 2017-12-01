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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * File descriptor metrics.
 *
 * @author Michael Weirauch
 */
class FileDescriptorMetricsTest {

    private interface HotSpotLikeOperatingSystemMXBean extends OperatingSystemMXBean {
        long getOpenFileDescriptorCount();
        long getMaxFileDescriptorCount();
    }

    @Test
    @SuppressWarnings("restriction")
    void fileDescriptorMetricsRuntime() {
        // currently only supporting HotSpot JVM
        final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        assumeTrue(osBean instanceof com.sun.management.UnixOperatingSystemMXBean);

        final MeterRegistry registry = new SimpleMeterRegistry();
        new FileDescriptorMetrics().bindTo(registry);

        assertThat(registry.find("process.open.fds").gauge()).isPresent();
        assertThat(registry.find("process.max.fds").gauge()).isPresent();
    }

    @Test
    void fileDescriptorMetricsUnsupportedOsBeanMock() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final OperatingSystemMXBean osBean = mock(OperatingSystemMXBean.class);
        new FileDescriptorMetrics(osBean, Tags.zip("some", "tag")).bindTo(registry);

        assertThat(registry.find("process.open.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(Double.NaN);
        assertThat(registry.find("process.max.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(Double.NaN);
    }

    @Test
    void fileDescriptorMetricsSupportedOsBeanMock() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final HotSpotLikeOperatingSystemMXBean osBean = mock(
            HotSpotLikeOperatingSystemMXBean.class);
        when(osBean.getOpenFileDescriptorCount()).thenReturn(Long.valueOf(512));
        when(osBean.getMaxFileDescriptorCount()).thenReturn(Long.valueOf(1024));
        new FileDescriptorMetrics(osBean, Tags.zip("some", "tag")).bindTo(registry);

        assertThat(registry.find("process.open.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(512.0);
        assertThat(registry.find("process.max.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(1024.0);
    }

    @Test
    void fileDescriptorMetricsInvocationException() {
        final MeterRegistry registry = new SimpleMeterRegistry();
        final HotSpotLikeOperatingSystemMXBean osBean = mock(
            HotSpotLikeOperatingSystemMXBean.class);
        when(osBean.getOpenFileDescriptorCount()).thenThrow(InvocationTargetException.class);
        when(osBean.getMaxFileDescriptorCount()).thenThrow(InvocationTargetException.class);
        new FileDescriptorMetrics(osBean, Tags.zip("some", "tag")).bindTo(registry);

        assertThat(registry.find("process.open.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(Double.NaN);
        assertThat(registry.find("process.max.fds").tags("some", "tag")
            .gauge().map(Gauge::value)).hasValue(Double.NaN);
    }
}
