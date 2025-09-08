package io.micrometer.core.instrument.binder.jvm.convention.otel;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmCpuMeterConventions;

/**
 * Conventions for JVM CPU related metrics based on OpenTelemetry semantic conventions.
 *
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/runtime/jvm-metrics.md">OpenTelemtry
 * Semantic conventions for JVM metrics v1.37.0</a>
 * @see io.micrometer.core.instrument.binder.system.ProcessorMetrics
 * @since 1.16.0
 */
public class OpenTelemetryJvmCpuMeterConventions extends MicrometerJvmCpuMeterConventions {

    public OpenTelemetryJvmCpuMeterConventions(Tags extraTags) {
        super(extraTags);
    }

    @Override
    public MeterConvention<Object> cpuTimeConvention() {
        return () -> "jvm.cpu.time";
    }

    @Override
    public MeterConvention<Object> cpuCountConvention() {
        return () -> "jvm.cpu.count";
    }

    @Override
    public MeterConvention<Object> processCpuLoadConvention() {
        return () -> "jvm.cpu.recent_utilization";
    }

}
