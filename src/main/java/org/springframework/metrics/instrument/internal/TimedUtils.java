package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.annotation.TimedSet;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;

/**
 * @author Jon Schneider
 */
public class TimedUtils {
    public static Stream<Timed> findTimed(Class<?> clazz) {
        Timed t = clazz.getAnnotation(Timed.class);
        if(t != null)
            return of(t);

        TimedSet ts = clazz.getAnnotation(TimedSet.class);
        if(ts != null)
            return stream(ts.value()).sorted(comparing(Timed::value));

        return empty();
    }

    public static Stream<Timed> findTimed(Method m) {
        Timed t = m.getAnnotation(Timed.class);
        if(t != null)
            return of(t);

        TimedSet ts = m.getAnnotation(TimedSet.class);
        if(ts != null)
            return stream(ts.value()).sorted(comparing(Timed::value));

        return empty();
    }
}
