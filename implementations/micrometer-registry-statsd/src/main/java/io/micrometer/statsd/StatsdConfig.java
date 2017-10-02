package io.micrometer.statsd;

import io.micrometer.core.instrument.spectator.step.StepRegistryConfig;

public interface StatsdConfig extends StepRegistryConfig {
    default StatsdFlavor flavor() {
        String v = get(prefix() + ".flavor");

        if(v == null)
            return StatsdFlavor.Plain;

        for (StatsdFlavor flavor : StatsdFlavor.values()) {
            if(flavor.toString().equalsIgnoreCase(v))
                return flavor;
        }

        throw new IllegalArgumentException("Unrecognized statsd flavor '" + v + "' (check property " + prefix() + ".flavor)");
    }
}
