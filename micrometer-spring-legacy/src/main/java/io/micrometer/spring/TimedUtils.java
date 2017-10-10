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
package io.micrometer.spring;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.annotation.TimedSet;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.stream.Stream;

public class TimedUtils {
    public static Stream<Timed> findTimedAnnotations(AnnotatedElement element) {
        Timed t = AnnotationUtils.findAnnotation(element, Timed.class);
        if(t != null)
            return Stream.of(t);

        TimedSet ts = AnnotationUtils.findAnnotation(element, TimedSet.class);
        if(ts != null) {
            return Arrays.stream(ts.value());
        }

        return Stream.empty();
    }
}
