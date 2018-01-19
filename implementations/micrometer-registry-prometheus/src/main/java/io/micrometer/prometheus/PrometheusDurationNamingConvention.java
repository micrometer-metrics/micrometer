package io.micrometer.prometheus;

public class PrometheusDurationNamingConvention extends PrometheusNamingConvention {

    public PrometheusDurationNamingConvention() {
        super("_duration");
    }
}
