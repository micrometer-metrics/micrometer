/*
 * Copyright 2026 VMware, Inc.
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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.config.InvalidConfigurationException;

/**
 * <b>NOTE: This class is intended for internal use as an implementation detail. You
 * should not compile against its API. Please contact the project maintainers if you need
 * this as public API.</b>
 * <p>
 * Detects whether the optional HdrHistogram dependency is present on the runtime
 * classpath.
 * <p>
 * HdrHistogram is needed only by {@link TimeWindowPercentileHistogram}, which in turn is
 * used only when client-side percentiles are configured. Loading that histogram resolves
 * the HdrHistogram types it refers to, so callers must consult this class <em>before</em>
 * instantiating it (or any subclass of it). Otherwise a bare {@link NoClassDefFoundError}
 * escapes to the caller, which gives no indication of what is missing or why.
 *
 * @author Rafael Winterhalter
 * @since 1.18.0
 */
public final class HdrHistogramAvailability {

    /**
     * One of the two types {@link TimeWindowPercentileHistogram} refers to. Both ship in
     * the same artifact, so probing for either is sufficient.
     */
    private static final String PROBED_CLASS_NAME = "org.HdrHistogram.DoubleRecorder";

    private static final boolean AVAILABLE = probe();

    private HdrHistogramAvailability() {
    }

    private static boolean probe() {
        try {
            Class.forName(PROBED_CLASS_NAME, false, HdrHistogramAvailability.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }

    /**
     * Whether HdrHistogram can be loaded, and therefore whether client-side percentiles
     * can be computed. The classpath is probed once, when this class is initialized.
     * @return {@code true} if HdrHistogram is on the runtime classpath
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Verify that HdrHistogram is on the runtime classpath before a
     * {@link TimeWindowPercentileHistogram} is instantiated.
     * @throws InvalidConfigurationException if HdrHistogram is not on the runtime
     * classpath
     */
    public static void requireAvailable() {
        if (!AVAILABLE) {
            throw new InvalidConfigurationException(
                    "Client-side percentiles are configured (see publishPercentiles) but HdrHistogram, "
                            + "which Micrometer needs to compute them, is not on the runtime classpath. "
                            + "HdrHistogram is an optional dependency: add org.hdrhistogram:HdrHistogram to "
                            + "your application, or stop publishing percentiles. Note that "
                            + "publishPercentileHistogram and serviceLevelObjectives do not require HdrHistogram.");
        }
    }

}
