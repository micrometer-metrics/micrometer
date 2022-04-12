package io.micrometer.registry.otlp;

import io.micrometer.core.instrument.Meter;

interface StartTimeAwareMeter extends Meter {
    long getStartTimeNanos();
}
