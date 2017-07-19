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
package io.micrometer.core.instrument.internal;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class MapAccess {
    /**
     * This method should be used instead of the
     * {@link ConcurrentMap#computeIfAbsent(Object, Function)} call to minimize
     * thread contention. This method does not require locking for the common case
     * where the key exists, but potentially performs additional computation when
     * absent.
     */
    @SuppressWarnings("unchecked")
    public static <K, V, W extends V> W computeIfAbsent(ConcurrentMap<K, V> map, K k, Function<? super K, ? extends W> f) {
        V v = map.get(k);
        if (v == null) {
            V tmp = f.apply(k);
            v = map.putIfAbsent(k, tmp);
            if (v == null) {
                v = tmp;
            }
        }
        return (W) v;
    }
}
