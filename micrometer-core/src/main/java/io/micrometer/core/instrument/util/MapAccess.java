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
package io.micrometer.core.instrument.util;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * @author Jon Schneider
 */
public class MapAccess {
    /**
     * Convenience method for {@link ConcurrentMap#computeIfAbsent(Object, Function)} that casts the result
     * to the intended subtype.
     */
    @SuppressWarnings("unchecked")
    public static <K, V, W extends V> W computeIfAbsent(ConcurrentMap<K, V> map, K k, Function<? super K, ? extends W> f) {
        /**
         * TODO Look more carefully at {@link com.netflix.spectator.api.Utils#computeIfAbsent(ConcurrentMap, Object, Function)}
         * to see why it apparently doesn't satisfactorily prevent more than one insertion of a key. Perhaps they didn't
         * intend to prohibit this?
         */
        return (W) map.computeIfAbsent(k, f);
    }
}
