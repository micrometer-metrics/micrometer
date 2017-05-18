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
