package io.micrometer.core.instrument.binder.jvm.convention.otel;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterConvention;
import io.micrometer.core.instrument.binder.SimpleMeterConvention;
import io.micrometer.core.instrument.binder.jvm.convention.micrometer.MicrometerJvmClassLoadingMeterConventions;

/**
 * Conventions for JVM class loading metrics based on OpenTelemetry semantic conventions.
 *
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/blob/v1.37.0/docs/runtime/jvm-metrics.md">OpenTelemtry
 * Semantic conventions for JVM metrics v1.37.0</a>
 * @see io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
 * @since 1.16.0
 */
public class OpenTelemetryJvmClassLoadingMeterConventions extends MicrometerJvmClassLoadingMeterConventions {

    public OpenTelemetryJvmClassLoadingMeterConventions() {
        super();
    }

    public OpenTelemetryJvmClassLoadingMeterConventions(Tags extraTags) {
        super(extraTags);
    }

    @Override
    public MeterConvention<Object> loadedConvention() {
        return new SimpleMeterConvention<>("jvm.class.loaded", getCommonTags());
    }

    @Override
    public MeterConvention<Object> unloadedConvention() {
        return new SimpleMeterConvention<>("jvm.class.unloaded", getCommonTags());
    }

    @Override
    public MeterConvention<Object> currentClassCountConvention() {
        return new SimpleMeterConvention<>("jvm.class.count", getCommonTags());
    }

}
