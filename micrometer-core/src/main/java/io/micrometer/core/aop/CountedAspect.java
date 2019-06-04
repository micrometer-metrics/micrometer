/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.lang.NonNullApi;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.util.function.Function;

/**
 * Aspect responsible for intercepting all methods annotated with the {@link Counted}
 * annotation and record a few counter metrics about their execution status.
 *
 * @author Ali Dehghani
 * @see Counted
 */
@Aspect
@NonNullApi
public class CountedAspect {

    /**
     * The tag name to encapsulate the method execution status.
     */
    private static final String STATUS_TAG = "status";

    /**
     * The tag name to encapsulate the exception thrown by the intercepted method.
     */
    private static final String EXCEPTION_TAG = "exception";

    /**
     * Where we're going register metrics.
     */
    private final MeterRegistry meterRegistry;

    /**
     * A function to produce additional tags for any given join point.
     */
    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    /**
     * Construct a new aspect with the given {@code meterRegistry} along with a default
     * tags provider.
     *
     * @param meterRegistry Where we're going register metrics.
     */
    public CountedAspect(MeterRegistry meterRegistry) {
        this(meterRegistry, pjp ->
                Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(),
                        "method", pjp.getStaticPart().getSignature().getName()));
    }

    /**
     * Constructs a new aspect with the given {@code meterRegistry} and tags provider function.
     *
     * @param meterRegistry        Where we're going register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public CountedAspect(MeterRegistry meterRegistry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this.meterRegistry = meterRegistry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
    }

    /**
     * Intercept methods annotated with the {@link Counted} annotation and expose a few counters about
     * their execution status. By default, this aspect records only failed attempts. If the
     * {@link Counted#successfulAttempts()} is set to {@code true}, then the aspect would record all
     * successful attempts, too. In case of a failure, the aspect tags the counter with the simple name
     * of the thrown exception.
     *
     * @param pjp     Encapsulates some information about the intercepted area.
     * @param counted The annotation.
     * @return Whatever the intercepted method returns.
     * @throws Throwable When the intercepted method throws one.
     */
    @Around("@annotation(counted)")
    public Object interceptAndRecord(ProceedingJoinPoint pjp, Counted counted) throws Throwable {
        try {
            Object result = pjp.proceed();
            if (counted.successfulAttempts()) {
                counter(pjp, counted).tag(STATUS_TAG, "succeed").register(meterRegistry).increment();
            }

            return result;
        } catch (Throwable e) {
            counter(pjp, counted)
                    .tag(EXCEPTION_TAG, e.getClass().getSimpleName())
                    .tag(STATUS_TAG, "failed")
                    .register(meterRegistry)
                    .increment();

            throw e;
        }
    }

    private Counter.Builder counter(ProceedingJoinPoint pjp, Counted counted) {
        return Counter.builder(counted.value()).
                description(counted.description())
                .tags(tagsBasedOnJoinPoint.apply(pjp));
    }
}
