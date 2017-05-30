package org.springframework.metrics.instrument.internal;

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
