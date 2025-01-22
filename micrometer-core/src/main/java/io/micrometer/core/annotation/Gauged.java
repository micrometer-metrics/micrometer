package io.micrometer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotated methods are instrumented using a {@code Gauge}
 *
 * @author Sandeep Vishnu
 * @see io.micrometer.core.aop.GaugedAspect
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Gauged {
    /**
     * Name of the gauge metric.
     *
     * @return gauge name
     */
    String value() default "";

    /**
     * Description of the gauge.
     *
     * @return gauge description
     */
    String description() default "";

    /**
     * Additional tags for the gauge.
     *
     * @return array of tag strings in the form key:value
     */
    String[] extraTags() default {};
}
