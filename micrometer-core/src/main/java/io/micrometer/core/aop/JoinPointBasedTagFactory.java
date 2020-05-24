package io.micrometer.core.aop;

import io.micrometer.core.instrument.Tag;
import java.util.function.Function;
import org.aspectj.lang.ProceedingJoinPoint;

@FunctionalInterface
public interface JoinPointBasedTagFactory extends Function<ProceedingJoinPoint, Iterable<Tag>> {
}
