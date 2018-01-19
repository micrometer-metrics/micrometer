/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.atlas;

import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.lang.Nullable;

import static com.netflix.spectator.api.Statistic.*;

public class AtlasUtils {
    @Nullable
    static com.netflix.spectator.api.Statistic toSpectatorStatistic(Statistic stat) {
        switch (stat) {
            case Count:
                return count;
            case TotalTime:
                return totalTime;
            case Total:
                return totalAmount;
            case Value:
                return gauge;
            case ActiveTasks:
                return activeTasks;
            case Duration:
                return duration;
            case Max:
                return max;
        }
        return null;
    }
}
