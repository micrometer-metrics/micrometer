package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.push.PushRegistryConfig;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

public interface OtlpConfig extends PushRegistryConfig {

    /**
     * Configuration with default values.
     */
    OtlpConfig DEFAULT = key -> null;

    @Override
    default String prefix() {
        return "otlp";
    }

    /**
     * Defaults to http://localhost:4318/v1/metrics
     * @return address to where metrics will be published.
     */
    default String url() {
        return getUrlString(this, "url").orElse("http://localhost:4318/v1/metrics");
    }

    // TODO config option to control transport/encoding: http json, http protobuf, grpc
    // for now, http protobuf only

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> PushRegistryConfig.validate(c),
                checkRequired("url", OtlpConfig::url)
        );
    }
}
