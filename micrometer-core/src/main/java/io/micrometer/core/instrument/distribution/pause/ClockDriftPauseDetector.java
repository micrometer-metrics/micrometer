/*
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.core.instrument.distribution.pause;

import java.time.Duration;

/**
 * Pause detector that monitors clock drift at a configurable interval.
 *
 * @author Jon Schneider
 */
public class ClockDriftPauseDetector implements PauseDetector {

    private final Duration sleepInterval;

    private final Duration pauseThreshold;

    public ClockDriftPauseDetector(Duration sleepInterval, Duration pauseThreshold) {
        this.sleepInterval = sleepInterval;
        this.pauseThreshold = pauseThreshold;
    }

    public Duration getSleepInterval() {
        return sleepInterval;
    }

    public Duration getPauseThreshold() {
        return pauseThreshold;
    }

}
