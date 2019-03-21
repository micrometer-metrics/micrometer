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
package io.micrometer.spring.scheduling;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.spring.TimedUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@NonNullApi
@NonNullFields
@Aspect
public class ScheduledMethodMetrics {
    private static final Log logger = LogFactory.getLog(ScheduledMethodMetrics.class);

    private final MeterRegistry registry;

    public ScheduledMethodMetrics(MeterRegistry registry) {
        this.registry = registry;
    }


    @Around("execution (@org.springframework.scheduling.annotation.Scheduled  * *.*(..))")
    public Object timeScheduledOperation(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String signature = pjp.getSignature().toShortString();

        if (method.getDeclaringClass().isInterface()) {
            try {
                method = pjp.getTarget().getClass().getDeclaredMethod(pjp.getSignature().getName(),
                    method.getParameterTypes());
            } catch (final SecurityException | NoSuchMethodException e) {
                logger.warn("Unable to perform metrics timing on " + signature, e);
                return pjp.proceed();
            }
        }

        Timer shortTaskTimer = null;
        LongTaskTimer longTaskTimer = null;

        for (Timed timed : TimedUtils.findTimedAnnotations(method)) {
            if (timed.longTask())
                longTaskTimer = LongTaskTimer.builder(timed.value())
                    .tags(timed.extraTags())
                    .description("Timer of @Scheduled long task")
                    .register(registry);
            else {
                Timer.Builder timerBuilder = Timer.builder(timed.value())
                    .tags(timed.extraTags())
                    .description("Timer of @Scheduled task");

                if (timed.percentiles().length > 0) {
                    timerBuilder = timerBuilder.publishPercentiles(timed.percentiles());
                }

                shortTaskTimer = timerBuilder.register(registry);
            }
        }

        if (shortTaskTimer != null && longTaskTimer != null) {
            final Timer finalTimer = shortTaskTimer;
            //noinspection NullableProblems
            return recordThrowable(longTaskTimer, () -> recordThrowable(finalTimer, pjp::proceed));
        } else if (shortTaskTimer != null) {
            //noinspection NullableProblems
            return recordThrowable(shortTaskTimer, pjp::proceed);
        } else if (longTaskTimer != null) {
            //noinspection NullableProblems
            return recordThrowable(longTaskTimer, pjp::proceed);
        }

        return pjp.proceed();
    }

    private Object recordThrowable(LongTaskTimer timer, ThrowableCallable f) throws Throwable {
        LongTaskTimer.Sample timing = timer.start();
        try {
            return f.call();
        } finally {
            timing.stop();
        }
    }

    private Object recordThrowable(Timer timer, ThrowableCallable f) throws Throwable {
        long start = registry.config().clock().monotonicTime();
        try {
            return f.call();
        } finally {
            timer.record(registry.config().clock().monotonicTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private interface ThrowableCallable {
        Object call() throws Throwable;
    }
}