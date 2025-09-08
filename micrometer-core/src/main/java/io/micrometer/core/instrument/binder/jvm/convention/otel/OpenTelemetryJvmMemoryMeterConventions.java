package io.micrometer.core.instrument.binder.jvm.convention.otel;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmMemoryMeterConventions;

import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * Conventions for JVM memory metrics based on OpenTelemetry semantic conventions.
 *
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/runtime/jvm-metrics.md">OpenTelemtry
 * Semantic conventions for JVM metrics v1.37.0</a>
 * @see io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
 * @since 1.16.0
 */
public class OpenTelemetryJvmMemoryMeterConventions extends MicrometerJvmMemoryMeterConventions {

    public OpenTelemetryJvmMemoryMeterConventions(Tags extraTags) {
        super(extraTags);
    }

    @Override
    protected Tags getCommonTags(MemoryPoolMXBean memoryPoolBean) {
        return this.extraTags.and(Tags.of("jvm.memory.pool.name", memoryPoolBean.getName(), "jvm.memory.type",
                MemoryType.HEAP.equals(memoryPoolBean.getType()) ? "heap" : "non_heap"));
    }

    @Override
    public MeterConvention<MemoryPoolMXBean> getMemoryMaxConvention() {
        return new SimpleMeterConvention<>("jvm.memory.limit", this::getCommonTags);
    }

}
