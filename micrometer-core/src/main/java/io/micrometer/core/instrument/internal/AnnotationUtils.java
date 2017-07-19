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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;

/**
 * @author Jon Schneider
 */
public class AnnotationUtils {
    public static Stream<Timed> findTimed(Class<?> clazz) {
        return findTimed(clazz.getAnnotation(Timed.class), clazz.getAnnotation(TimedSet.class));
    }

    public static Stream<Timed> findTimed(Method m) {
        return findTimed(m.getAnnotation(Timed.class), m.getAnnotation(TimedSet.class));
    }

    private static Stream<Timed> findTimed(Timed t, TimedSet ts) {
        if(t != null)
            return of(t);
        if(ts != null)
            return stream(ts.value()).sorted(comparing(Timed::value));
        return empty();
    }
}
