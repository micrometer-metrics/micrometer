/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.internal;

import java.util.concurrent.TimeUnit;

public class TimeUtils {
    public static double convert(double t, TimeUnit sourceUnit, TimeUnit destinationUnit) {
        switch(sourceUnit) {
            case NANOSECONDS:
                return nanosToUnit(t, destinationUnit);
            case MICROSECONDS:
                return microsToUnit(t, destinationUnit);
            case MILLISECONDS:
                return millisToUnit(t, destinationUnit);
            case SECONDS:
                return secondsToUnit(t, destinationUnit);
            case MINUTES:
                return minutesToUnit(t, destinationUnit);
            case HOURS:
                return hoursToUnit(t, destinationUnit);
            case DAYS:
            default:
                return daysToUnit(t, destinationUnit);
        }
    }

    public static double nanosToUnit(double nanos, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
            default:
                return nanos;
            case MICROSECONDS:
                return nanos / (C1 / C0);
            case MILLISECONDS:
                return nanos / (C2 / C0);
            case SECONDS:
                return nanos / (C3 / C0);
            case MINUTES:
                return nanos / (C4 / C0);
            case HOURS:
                return nanos / (C5 / C0);
            case DAYS:
                return nanos / (C6 / C0);
        }
    }

    public static double microsToUnit(double micros, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return micros * (C1 / C0);
            case MICROSECONDS:
            default:
                return micros;
            case MILLISECONDS:
                return micros / (C2 / C1);
            case SECONDS:
                return micros / (C3 / C1);
            case MINUTES:
                return micros / (C4 / C1);
            case HOURS:
                return micros / (C5 / C1);
            case DAYS:
                return micros / (C6 / C1);
        }
    }

    public static double millisToUnit(double millis, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return millis * (C2 / C0);
            case MICROSECONDS:
                return millis * (C2 / C1);
            case MILLISECONDS:
            default:
                return millis;
            case SECONDS:
                return millis / (C3 / C2);
            case MINUTES:
                return millis / (C4 / C2);
            case HOURS:
                return millis / (C5 / C2);
            case DAYS:
                return millis / (C6 / C2);
        }
    }

    public static double secondsToUnit(double seconds, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return seconds * (C3 / C0);
            case MICROSECONDS:
                return seconds * (C3 / C1);
            case MILLISECONDS:
                return seconds * (C3 / C2);
            case SECONDS:
            default:
                return seconds;
            case MINUTES:
                return seconds / (C4 / C3);
            case HOURS:
                return seconds / (C5 / C3);
            case DAYS:
                return seconds / (C6 / C3);
        }
    }

    public static double minutesToUnit(double minutes, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return minutes * (C4 / C0);
            case MICROSECONDS:
                return minutes * (C4 / C1);
            case MILLISECONDS:
                return minutes * (C4 / C2);
            case SECONDS:
                return minutes * (C4 / C3);
            case MINUTES:
            default:
                return minutes;
            case HOURS:
                return minutes / (C5 / C4);
            case DAYS:
                return minutes / (C6 / C4);
        }
    }

    public static double hoursToUnit(double hours, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return hours * (C5 / C0);
            case MICROSECONDS:
                return hours * (C5 / C1);
            case MILLISECONDS:
                return hours * (C5 / C2);
            case SECONDS:
                return hours * (C5 / C3);
            case MINUTES:
                return hours * (C5 / C4);
            case HOURS:
            default:
                return hours;
            case DAYS:
                return hours / (C6 / C5);
        }
    }

    public static double daysToUnit(double days, TimeUnit destinationUnit) {
        switch (destinationUnit) {
            case NANOSECONDS:
                return days * (C6 / C0);
            case MICROSECONDS:
                return days * (C6 / C1);
            case MILLISECONDS:
                return days * (C6 / C2);
            case SECONDS:
                return days * (C6 / C3);
            case MINUTES:
                return days * (C6 / C4);
            case HOURS:
                return days * (C6 / C5);
            case DAYS:
            default:
                return days;
        }
    }

    private static final long C0 = 1L;
    private static final long C1 = C0 * 1000L;
    private static final long C2 = C1 * 1000L;
    private static final long C3 = C2 * 1000L;
    private static final long C4 = C3 * 60L;
    private static final long C5 = C4 * 60L;
    private static final long C6 = C5 * 24L;
}
