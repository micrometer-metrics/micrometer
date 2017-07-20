/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.metrics.scheduling;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.internal.AnnotationUtils;
import io.micrometer.core.instrument.stats.quantile.WindowSketchQuantiles;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
public class MetricsSchedulingAspect {
    private static final Log logger = LogFactory.getLog(MetricsSchedulingAspect.class);

    private final MeterRegistry registry;

    public MetricsSchedulingAspect(MeterRegistry registry) {
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

        for (Timed timed : AnnotationUtils.findTimed(method).toArray(Timed[]::new)) {
            if(timed.longTask())
                longTaskTimer = registry.longTaskTimer(timed.value(), timed.extraTags());
            else {
                Timer.Builder timerBuilder = registry.timerBuilder(timed.value())
                        .tags(timed.extraTags());
                if(timed.quantiles().length > 0) {
                    timerBuilder = timerBuilder.quantiles(WindowSketchQuantiles.quantiles(timed.quantiles()).create());
                }
                shortTaskTimer = timerBuilder.create();
            }
        }

        if(shortTaskTimer != null && longTaskTimer != null) {
            final Timer finalTimer = shortTaskTimer;
            return recordThrowable(longTaskTimer, () -> recordThrowable(finalTimer, pjp::proceed));
        }
        else if(shortTaskTimer != null) {
            return recordThrowable(shortTaskTimer, pjp::proceed);
        }
        else if(longTaskTimer != null) {
            return recordThrowable(longTaskTimer, pjp::proceed);
        }

        return pjp.proceed();
    }

    private Object recordThrowable(LongTaskTimer timer, ThrowableCallable f) throws Throwable {
        long id = timer.start();
        try {
            return f.call();
        } finally {
            timer.stop(id);
        }
    }

    private Object recordThrowable(Timer timer, ThrowableCallable f) throws Throwable {
        long start = registry.getClock().monotonicTime();
        try {
            return f.call();
        } finally {
            timer.record(registry.getClock().monotonicTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    private interface ThrowableCallable {
        Object call() throws Throwable;
    }
}