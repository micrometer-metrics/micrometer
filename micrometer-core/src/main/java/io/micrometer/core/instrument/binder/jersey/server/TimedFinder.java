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
package io.micrometer.core.instrument.binder.jersey.server;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
class TimedFinder {

    private final AnnotationFinder annotationFinder;

    TimedFinder(AnnotationFinder annotationFinder) {
        this.annotationFinder = annotationFinder;
    }

    Set<Timed> findTimedAnnotations(AnnotatedElement element) {
        Timed t = annotationFinder.findAnnotation(element, Timed.class);
        if (t != null)
            return Collections.singleton(t);

        TimedSet ts = annotationFinder.findAnnotation(element, TimedSet.class);
        if (ts != null) {
            return Arrays.stream(ts.value()).collect(Collectors.toSet());
        }

        return Collections.emptySet();
    }

}
