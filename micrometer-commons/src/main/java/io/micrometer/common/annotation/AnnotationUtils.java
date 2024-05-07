/*
 * Copyright 2023 VMware, Inc.
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
package io.micrometer.common.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Utility class that can verify whether the method is annotated with the Micrometer
 * annotations.
 *
 * Code ported from Spring Cloud Sleuth.
 *
 * @author Christian Schwerdtfeger
 */
final class AnnotationUtils {

    private AnnotationUtils() {

    }

    static List<AnnotatedParameter> findAnnotatedParameters(Class<? extends Annotation> annotationClazz, Method method,
            Object[] args) {
        Parameter[] parameters = method.getParameters();
        return IntStream.range(0, parameters.length).boxed().flatMap(parameterIndex -> {
            Parameter parameter = parameters[parameterIndex];
            Annotation[] annotations = parameter.getAnnotationsByType(annotationClazz);
            if (annotations.length == 0) {
                return Stream.empty();
            }
            return Arrays.stream(annotations)
                .map(annotation -> new AnnotatedParameter(parameterIndex, annotation, args[parameterIndex]));
        }).collect(Collectors.toList());
    }

}
