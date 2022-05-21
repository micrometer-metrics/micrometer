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
package io.micrometer.core.instrument.util;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.Meter;

/**
 * @author Jon Schneider
 */
public final class MeterEquivalence {

    private MeterEquivalence() {
    }

    public static boolean equals(@Nullable Meter m1, @Nullable Object o) {
        if (m1 == null && o != null)
            return false;
        if (o == null && m1 != null)
            return false;
        if (!(o instanceof Meter))
            return false;
        if (m1 == o)
            return true;
        Meter m2 = (Meter) o;
        return m1.getId().equals(m2.getId());
    }

    public static int hashCode(Meter m) {
        return m.getId().hashCode();
    }

}
