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
import java.util.ArrayList;
import java.util.List;

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
        Annotation[][] parameters = method.getParameterAnnotations();
        List<AnnotatedParameter> result = new ArrayList<>();
        int i = 0;
        for (Annotation[] parameter : parameters) {
            for (Annotation parameter2 : parameter) {
                if (annotationClazz.isAssignableFrom(parameter2.annotationType())) {
                    result.add(new AnnotatedParameter(i, parameter2, args[i]));
                }
            }
            i++;
        }
        return result;
    }

}
