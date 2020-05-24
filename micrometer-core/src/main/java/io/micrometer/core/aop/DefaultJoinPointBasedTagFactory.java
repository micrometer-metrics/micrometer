package io.micrometer.core.aop;

import io.micrometer.core.annotation.MetricTag;
import io.micrometer.core.annotation.MetricTag.Converter;
import io.micrometer.core.instrument.Tag;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

public class DefaultJoinPointBasedTagFactory implements JoinPointBasedTagFactory {
    @Override
    public Iterable<Tag> apply(ProceedingJoinPoint proceedingJoinPoint) {
        Set<Tag> tags = new HashSet<>();
        tags.add(Tag.of("class", proceedingJoinPoint.getStaticPart().getSignature().getDeclaringTypeName()));
        tags.add(Tag.of("method", proceedingJoinPoint.getStaticPart().getSignature().getName()));

        Parameter[] parameters = ((MethodSignature) proceedingJoinPoint.getSignature()).getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            for (Annotation annotation : parameters[i].getAnnotations()) {
                if (annotation instanceof MetricTag) {
                    MetricTag metricTag = (MetricTag) annotation;
                    String key = metricTag.value();
                    String value = createConverter(metricTag.converter()).apply(proceedingJoinPoint.getArgs()[i]);

                    tags.add(Tag.of(key, value));
                }
            }
        }

        return tags;
    }

    private Converter createConverter(Class<? extends Converter> clazz) {
        try {
            // TODO: memoize the instance (class -> instance)
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to create converter instance: " + clazz.getName());
        }
    }
}
