package org.springframework.metrics.instrument.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {
    String value() default "";

    String[] extraTags() default {};
}
