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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * @deprecated since 1.13.0 use the jersey-micrometer module in the Jersey project instead
 */
@Deprecated
public interface AnnotationFinder {

    AnnotationFinder DEFAULT = new AnnotationFinder() {
    };

    /**
     * The default implementation performs a simple search for a declared annotation
     * matching the search type. Spring provides a more sophisticated annotation search
     * utility that matches on meta-annotations as well.
     * @param annotatedElement The element to search.
     * @param annotationType The annotation type class.
     * @param <A> Annotation type to search for.
     * @return A matching annotation.
     */
    @SuppressWarnings("unchecked")
    default <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        Annotation[] anns = annotatedElement.getDeclaredAnnotations();
        for (Annotation ann : anns) {
            if (ann.annotationType() == annotationType) {
                return (A) ann;
            }
        }
        return null;
    }

}
