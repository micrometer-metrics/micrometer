package io.micrometer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * This is a feature that may still yet change before 1.0.0 GA.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Incubating {
    String since();
}
