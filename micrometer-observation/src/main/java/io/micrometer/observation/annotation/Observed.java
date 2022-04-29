package io.micrometer.observation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micrometer.observation.Observation;

/**
 * @author Jonatan Ivanov
 * @since 1.10.0
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Observed {

    /**
     * Name of the {@link Observation}.
     *
     * @return name of the {@link Observation}
     */
    String value() default "";
}
