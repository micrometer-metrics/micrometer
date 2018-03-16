/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.function.Function;


/**
 * AspectJ aspect for intercepting types or method annotated with @Timed.
 *
 * @author David J. M. Karlsen
 * @author Jon Schneider
 */
@Aspect
@NonNullApi
@Incubating(since = "1.0.0")
public class TimedAspect {
    public static final String DEFAULT_METRIC_NAME = "method.timed";
    private final MeterRegistry registry;
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinpoint;

    public TimedAspect(MeterRegistry registry) {
        this(registry, pjp ->
                Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                        "method", pjp.getStaticPart().getSignature().getName())
        );
    }

    public TimedAspect(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinpoint) {
        this.registry = registry;
        this.tagsBasedOnJoinpoint = tagsBasedOnJoinpoint;
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    public Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();

        Timer.Sample sample = Timer.start(registry);
        try {
            return pjp.proceed();
        } finally {
            sample.stop(Timer.builder(metricName)
                    .description(timed.description().isEmpty() ? null : timed.description())
                    .tags(timed.extraTags())
                    .tags(tagsBasedOnJoinpoint.apply(pjp))
                    .publishPercentileHistogram(timed.histogram())
                    .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                    .register(registry));
        }
    }
}
