package io.micrometer.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface MetricTag {
    String value();
    Class<? extends Converter> converter() default ToStringConverter.class;

    interface Converter extends Function<Object, String> {
    }

    final class ToStringConverter implements Converter {
        @Override
        public String apply(Object object) {
            return String.valueOf(object);
        }
    }
}
